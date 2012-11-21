package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class LayoutDeviceManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutDeviceManager");
  private static final String LAYOUT_DEVICES_XSD = "layout-devices.xsd";

  static final String LAYOUT_DEVICES_NAMESPACE = "http://schemas.android.com/sdk/android/layout-devices/1";

  private final List<LayoutDevice> myUserLayoutDevices = new ArrayList<LayoutDevice>();
  private final SAXParserFactory myParserFactory;

  private List<LayoutDevice> myDefaultLayoutDevices = new ArrayList<LayoutDevice>();
  private List<LayoutDevice> myAddOnLayoutDevices = new ArrayList<LayoutDevice>();
  private List<LayoutDevice> myLayoutDevices;

  public LayoutDeviceManager() {
    myParserFactory = SAXParserFactory.newInstance();
    myParserFactory.setNamespaceAware(true);
  }

  @NotNull
  public List<LayoutDevice> getCombinedList() {
    return myLayoutDevices != null
           ? myLayoutDevices
           : Collections.<LayoutDevice>emptyList();
  }

  @Nullable
  public LayoutDevice getUserLayoutDevice(String name) {
    for (LayoutDevice d : myUserLayoutDevices) {
      if (d.getName().equals(name)) {
        return d;
      }
    }
    return null;
  }

  @NotNull
  public LayoutDevice addUserDevice(@NotNull String name, float xdpi, float ydpi) {
    final LayoutDevice device = new LayoutDevice(name, LayoutDevice.Type.CUSTOM);
    device.setXDpi(xdpi);
    device.setYDpi(ydpi);
    myUserLayoutDevices.add(device);
    combineLayoutDevices();
    return device;
  }

  public void removeUserDevice(LayoutDevice device) {
    if (myUserLayoutDevices.remove(device)) {
      combineLayoutDevices();
    }
  }

  @NotNull
  public LayoutDevice replaceUserDevice(@NotNull LayoutDevice device, @NotNull String newName, float newXDpi, float newYDpi) {
    if (device.getName().equals(newName) && device.getXDpi() == newXDpi && device.getYDpi() == newYDpi) {
      return device;
    }

    final LayoutDevice newDevice = new LayoutDevice(newName, LayoutDevice.Type.CUSTOM);
    newDevice.setXDpi(newXDpi);
    newDevice.setYDpi(newYDpi);

    final List<LayoutDeviceConfiguration> configs = device.getConfigurations();
    newDevice.addConfigs(configs);

    myUserLayoutDevices.remove(device);
    myUserLayoutDevices.add(newDevice);
    combineLayoutDevices();
    return newDevice;
  }

  @Nullable
  public LayoutDeviceConfiguration addUserConfiguration(@NotNull LayoutDevice device,
                                                        @NotNull String configurationName,
                                                        @NotNull FolderConfiguration configuration) {
    if (myUserLayoutDevices.contains(device)) {
      return device.addConfig(configurationName, configuration);
    }
    return null;
  }

  @Nullable
  public LayoutDeviceConfiguration replaceUserConfiguration(@NotNull LayoutDevice device,
                                                            @Nullable String oldConfigurationName,
                                                            @NotNull String newConfigurationName,
                                                            @NotNull FolderConfiguration configuration) {
    if (myUserLayoutDevices.contains(device)) {
      if (oldConfigurationName != null && !oldConfigurationName.equals(newConfigurationName)) {
        device.removeConfig(oldConfigurationName);
      }
      return device.addConfig(newConfigurationName, configuration);
    }
    return null;
  }

  public void removeUserConfiguration(LayoutDevice device, String configName) {
    if (myUserLayoutDevices.contains(device)) {
      device.removeConfig(configName);
    }
  }

  public void saveUserDevices() {
    try {
      final String userFolder = AndroidLocation.getFolder();
      final File deviceXmlFile = new File(userFolder, SdkConstants.FN_DEVICES_XML);
      if (!deviceXmlFile.isDirectory()) {
        write(deviceXmlFile, myUserLayoutDevices);
      }
    }
    catch (AndroidLocationException e) {
      LOG.info(e);
    }
  }

  public void loadDevices(@NotNull AndroidSdkData sdkData) {
    loadDefaultAndUserDevices(sdkData);
    loadAddOnLayoutDevices(sdkData);
  }

  private void loadDefaultAndUserDevices(@NotNull AndroidSdkData sdkData) {
    loadDefaultLayoutDevices(sdkData.getLocation());

    try {
      myUserLayoutDevices.clear();
      final String userFolder = AndroidLocation.getFolder();
      final File deviceXmlFile = new File(userFolder, SdkConstants.FN_DEVICES_XML);
      if (deviceXmlFile.isFile()) {
        parseLayoutDevices(deviceXmlFile, myUserLayoutDevices, LayoutDevice.Type.CUSTOM);
      }
    }
    catch (AndroidLocationException e) {
      LOG.info(e);
    }
  }

  private void parseAddOnLayoutDevice(File deviceXml) {
    myAddOnLayoutDevices = new ArrayList<LayoutDevice>();
    parseLayoutDevices(deviceXml, myAddOnLayoutDevices, LayoutDevice.Type.ADD_ON);
  }

  private void sealAddonLayoutDevices() {
    myAddOnLayoutDevices = Collections.unmodifiableList(myAddOnLayoutDevices);

    combineLayoutDevices();
  }

  private void loadAddOnLayoutDevices(@NotNull AndroidSdkData sdkData) {
    for (IAndroidTarget target : sdkData.getTargets()) {
      if (!target.isPlatform()) {
        File deviceXml = new File(target.getLocation(), SdkConstants.FN_DEVICES_XML);
        if (deviceXml.isFile()) {
          parseAddOnLayoutDevice(deviceXml);
        }
      }
    }
    sealAddonLayoutDevices();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void parseLayoutDevices(File deviceXml, List<LayoutDevice> list, LayoutDevice.Type deviceType) {
    try {
      final Source source = new StreamSource(new FileReader(deviceXml));
      final MyErrorHandler errorHandler = new MyErrorHandler();
      final Validator validator = getValidator(errorHandler);
      validator.validate(source);

      if (!errorHandler.foundError()) {
        final LayoutDeviceHandler handler = new LayoutDeviceHandler(deviceType);
        final SAXParser parser = myParserFactory.newSAXParser();
        parser.parse(new InputSource(new FileInputStream(deviceXml)), handler);
        list.addAll(handler.getDevices());
      }
    }
    catch (SAXException e) {
      LOG.info(e);
    }
    catch (FileNotFoundException e) {
      LOG.info(e);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (ParserConfigurationException e) {
      LOG.info(e);
    }
  }

  private static Validator getValidator(ErrorHandler handler) throws SAXException {
    final InputStream xsdStream = LayoutDeviceManager.class.getResourceAsStream(LAYOUT_DEVICES_XSD);
    final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    final Schema schema = factory.newSchema(new StreamSource(xsdStream));
    final Validator validator = schema.newValidator();
    if (handler != null) {
      validator.setErrorHandler(handler);
    }
    return validator;
  }

  private void loadDefaultLayoutDevices(String sdkOsLocation) {
    final List<LayoutDevice> devices = new ArrayList<LayoutDevice>();
    final File toolsFolder = new File(sdkOsLocation, SdkConstants.OS_SDK_TOOLS_LIB_FOLDER);
    if (toolsFolder.isDirectory()) {
      File deviceXml = new File(toolsFolder, SdkConstants.FN_DEVICES_XML);
      if (deviceXml.isFile()) {
        parseLayoutDevices(deviceXml, devices, LayoutDevice.Type.PLATFORM);
      }
    }
    myDefaultLayoutDevices = Collections.unmodifiableList(devices);
  }

  private void combineLayoutDevices() {
    final List<LayoutDevice> devices = new ArrayList<LayoutDevice>();
    devices.addAll(myDefaultLayoutDevices);
    devices.addAll(myAddOnLayoutDevices);
    devices.addAll(myUserLayoutDevices);
    myLayoutDevices = Collections.unmodifiableList(devices);
  }

  private static void write(File deviceXml, List<LayoutDevice> deviceList) {
    try {
      final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      docFactory.setNamespaceAware(true);
      final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      final Document doc = docBuilder.newDocument();

      final Element baseNode = doc.createElementNS(LAYOUT_DEVICES_NAMESPACE, "layout-devices");
      baseNode.setPrefix("d");
      doc.appendChild(baseNode);

      for (LayoutDevice device : deviceList) {
        device.saveTo(doc, baseNode);
      }
      final Source source = new DOMSource(doc);
      final File file = new File(deviceXml.getAbsolutePath());
      final Result result = new StreamResult(file);
      final Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.transform(source, result);
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private static class MyErrorHandler implements ErrorHandler {
    private boolean myError = false;

    MyErrorHandler() {
    }

    public boolean foundError() {
      return myError;
    }

    public void error(SAXParseException ex) throws SAXException {
      myError = true;
      LOG.info(ex);
    }

    public void fatalError(SAXParseException ex) throws SAXException {
      myError = true;
      LOG.info(ex);
    }

    public void warning(SAXParseException ex) throws SAXException {
      LOG.debug(ex);
    }
  }
}
