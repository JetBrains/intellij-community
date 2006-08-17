package com.intellij.cvsSupport2.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CvsApplicationLevelConfiguration implements ApplicationComponent, JDOMExternalizable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration");

  public List<CvsRootConfiguration> CONFIGURATIONS = new ArrayList<CvsRootConfiguration>();

  public ExtConfiguration EXT_CONFIGURATION = new ExtConfiguration();
  public SshSettings SSH_CONFIGURATION = new SshSettings();
  public LocalSettings LOCAL_CONFIGURATION = new LocalSettings();
  public ProxySettings PROXY_SETTINGS = new ProxySettings();
  public SshSettings SSH_FOR_EXT_CONFIGURATION = new SshSettings();

  @NonNls private static final String CONFIGURATION_ELEMENT_NAME = "Configuration";
  public String PATH_TO_PASSWORD_FILE = null;
  public int TIMEOUT = 60;
  public boolean MAKE_CHECKED_OUT_FILES_READONLY = false;
  public boolean CHECKOUT_PRUNE_EMPTY_DIRECTORIES = true;
  public String CHECKOUT_KEYWORD_SUBSTITUTION = null;
  public boolean SHOW_RESTORE_DIRECTORIES_CONFIRMATION = true;

  @NotNull public String ENCODING;

  public boolean USE_GZIP = false;
  @NonNls public static final String DEFAULT = "Default";

  public boolean DO_OUTPUT = false;
  @NonNls private static final String USER_HOME_PROPERTY = "user.home";
  public boolean SEND_ENVIRONMENT_VARIABLES_TO_SERVER = false;

  public CvsApplicationLevelConfiguration() {
    ENCODING = DEFAULT;
  }


  public static CvsApplicationLevelConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(CvsApplicationLevelConfiguration.class);
  }

  public String getComponentName() {
    return "CvsApplicationLevelConfiguration";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    for (Iterator each = element.getChildren(CONFIGURATION_ELEMENT_NAME).iterator(); each.hasNext();) {
      Element child = (Element)each.next();
      CONFIGURATIONS.add(createConfigurationOn(child));
    }

    if (!encodingExists(ENCODING)) {
      ENCODING = DEFAULT;
    }

    updateConfigurations();

  }

  private boolean encodingExists(String encoding) {
    final Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();
    for (int i = 0; i < availableCharsets.length; i++) {
      Charset availableCharset = availableCharsets[i];
      if (availableCharset.name().equals(encoding)) {
        return true;
      }
    }
    return false;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (Iterator each = CONFIGURATIONS.iterator(); each.hasNext();) {
      createConfigurationElement((CvsRootConfiguration)each.next(), element);
    }
  }

  private void createConfigurationElement(CvsRootConfiguration configuration, Element element)
    throws WriteExternalException {
    Element child = new Element(CONFIGURATION_ELEMENT_NAME);
    configuration.writeExternal(child);
    element.addContent(child);
  }

  private CvsRootConfiguration createConfigurationOn(Element child) throws InvalidDataException {
    CvsRootConfiguration config = createNewConfiguration(this);
    config.readExternal(child);
    return config;
  }

  @NonNls private String defaultPathToPassFile() {
    return "$userdir" + "/.cvspass";
  }

  private boolean passFileExists() {
    if (PATH_TO_PASSWORD_FILE == null) return false;
    return new File(convertToIOFilePath(PATH_TO_PASSWORD_FILE)).isFile();
  }

  public static String convertToIOFilePath(String presentation) {
    String userHome = System.getProperty(USER_HOME_PROPERTY);
    userHome = userHome.replace(File.separatorChar, '/');
    presentation = presentation.replace(File.separatorChar, '/');
    try {
      String result = StringUtil.replace(presentation, "$userdir", userHome);
      result = result.replace('/', File.separatorChar);
      return result;
    }
    catch (Exception ex) {
      LOG.assertTrue(false, "userHome = " + userHome + ", presenation = " + presentation);
      return "";
    }
  }

  public String getPathToPassFile() {
    return convertToIOFilePath(getPathToPassFilePresentation());
  }

  public String getPathToPassFilePresentation() {
    if (!passFileExists()) {
      PATH_TO_PASSWORD_FILE = defaultPathToPassFile();
    }
    return PATH_TO_PASSWORD_FILE;
  }

  public CvsRootConfiguration getConfigurationForCvsRoot(String root) {
    for (Iterator each = CONFIGURATIONS.iterator(); each.hasNext();) {
      CvsRootConfiguration cvsRootConfiguration = (CvsRootConfiguration)each.next();
      if (cvsRootConfiguration.getCvsRootAsString().equals(root)) {
        return cvsRootConfiguration;
      }
    }
    CvsRootConfiguration newConfig = createNewConfiguration(this);
    newConfig.CVS_ROOT = root;
    CONFIGURATIONS.add(newConfig);
    return newConfig;

  }


  @NotNull public static String getCharset() {
    String value = getInstance().ENCODING;
    if (DEFAULT.equals(value)) {
      return CharsetToolkit.getDefaultSystemCharset().name();
    } else {
      return value;
    }

  }

  public void setPathToPasswordFile(final String text) {
    PATH_TO_PASSWORD_FILE = text;
    updateConfigurations();
  }

  private void updateConfigurations() {
    final File passFile = new File(getPathToPassFile());
    if (passFile.isFile()) {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(passFile)));
        try {
          String line;
          while ((line = reader.readLine()) != null) {
            final int sepPosition = line.indexOf(' ');
            if (sepPosition > 0) {
              final String cvsRoot = line.substring(0, sepPosition);
              tryToAddNewRoot(cvsRoot);
            }
          }

        } finally {
          reader.close();
        }
      } catch (IOException e) {
        //ignore
      }

    }
  }

  private void tryToAddNewRoot(final String cvsRoot) {
    for (CvsRootConfiguration configuration : CONFIGURATIONS) {
      if (Comparing.equal(configuration.getCvsRootAsString(), cvsRoot)) {
        return;
      }
    }

    final CvsRootConfiguration newConfiguration = createNewConfiguration(this);
    newConfiguration.CVS_ROOT = cvsRoot;
    CONFIGURATIONS.add(newConfiguration);
  }

  public static CvsRootConfiguration createNewConfiguration(CvsApplicationLevelConfiguration mainConfiguration) {
    final CvsRootConfiguration result = new CvsRootConfiguration();
    result.EXT_CONFIGURATION = mainConfiguration.EXT_CONFIGURATION.clone();
    result.SSH_CONFIGURATION = mainConfiguration.SSH_CONFIGURATION.clone();
    result.SSH_FOR_EXT_CONFIGURATION = mainConfiguration.SSH_FOR_EXT_CONFIGURATION.clone();
    result.LOCAL_CONFIGURATION = mainConfiguration.LOCAL_CONFIGURATION.clone();
    result.PROXY_SETTINGS = mainConfiguration.PROXY_SETTINGS.clone();
    return result;
  }
}
