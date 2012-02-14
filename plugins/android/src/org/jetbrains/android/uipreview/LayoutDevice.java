package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class LayoutDevice {
  private final String myName;
  private final List<LayoutDeviceConfiguration> myConfigurations = new ArrayList<LayoutDeviceConfiguration>();

  private List<LayoutDeviceConfiguration> mROList;
  private float mXDpi = Float.NaN;
  private float mYDpi = Float.NaN;

  private final Type myType;

  LayoutDevice(@NotNull String name, @NotNull Type type) {
    myName = name;
    myType = type;
  }

  void saveTo(Document doc, Element parentNode) {
    final Element deviceNode = createNode(doc, parentNode, "device");
    deviceNode.setAttribute("name", myName);

    final Element defaultNode = createNode(doc, deviceNode, "default");
    if (!Float.isNaN(mXDpi)) {
      final Element xdpiNode = createNode(doc, defaultNode, "xdpi");
      xdpiNode.setTextContent(Float.toString(mXDpi));
    }
    if (!Float.isNaN(mYDpi)) {
      final Element xdpiNode = createNode(doc, defaultNode, "ydpi");
      xdpiNode.setTextContent(Float.toString(mYDpi));
    }

    synchronized (myConfigurations) {
      for (LayoutDeviceConfiguration config : myConfigurations) {
        saveConfigurations(doc, deviceNode, config.getName(), config.getConfiguration());
      }
    }
  }

  private static Element createNode(Document doc, Element parentNode, String name) {
    Element newNode = doc.createElementNS(LayoutDeviceManager.LAYOUT_DEVICES_NAMESPACE, name);
    newNode.setPrefix(doc.lookupPrefix(LayoutDeviceManager.LAYOUT_DEVICES_NAMESPACE));
    parentNode.appendChild(newNode);
    return newNode;
  }

  private static void saveConfigurations(Document doc, Element parent, String configName, FolderConfiguration config) {
    final Element configNode = createNode(doc, parent, "config");
    configNode.setAttribute("name", configName);

    final CountryCodeQualifier countryCodeQualifier = config.getCountryCodeQualifier();
    if (countryCodeQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.COUNTRY_CODE_TAG_NAME);
      node.setTextContent(Integer.toString(countryCodeQualifier.getCode()));
    }

    final NetworkCodeQualifier networkCodeQualifier = config.getNetworkCodeQualifier();
    if (networkCodeQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.NETWORK_CODE_TAG_NAME);
      node.setTextContent(Integer.toString(networkCodeQualifier.getCode()));
    }

    final ScreenSizeQualifier screenSizeQualifier = config.getScreenSizeQualifier();
    if (screenSizeQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.SCREEN_SIZE_TAG_NAME);
      node.setTextContent(screenSizeQualifier.getFolderSegment());
    }

    final ScreenRatioQualifier screenRatioQualifier = config.getScreenRatioQualifier();
    if (screenRatioQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.SCREEN_RATIO_TAG_NAME);
      node.setTextContent(screenRatioQualifier.getFolderSegment());
    }

    final ScreenOrientationQualifier screenOrientationQualifier = config.getScreenOrientationQualifier();
    if (screenOrientationQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.SCREEN_ORIENTATION_TAG_NAME);
      node.setTextContent(screenOrientationQualifier.getFolderSegment());
    }

    final DensityQualifier pixelDensityQualifier = config.getDensityQualifier();
    if (pixelDensityQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.PIXEL_DENSITY_TAG_NAME);
      node.setTextContent(pixelDensityQualifier.getFolderSegment());
    }

    final TouchScreenQualifier touchTypeQualifier = config.getTouchTypeQualifier();
    if (touchTypeQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.TOUCH_TYPE_TAG_NAME);
      node.setTextContent(touchTypeQualifier.getFolderSegment());
    }

    final KeyboardStateQualifier keyboardStateQualifier = config.getKeyboardStateQualifier();
    if (keyboardStateQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.KEYBOARD_STATE_TAG_NAME);
      node.setTextContent(keyboardStateQualifier.getFolderSegment());
    }

    final TextInputMethodQualifier textInputMethodQualifier = config.getTextInputMethodQualifier();
    if (textInputMethodQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.TEXT_INPUT_METHOD_TAG_NAME);
      node.setTextContent(textInputMethodQualifier.getFolderSegment());
    }

    final NavigationStateQualifier navigationStateQualifier = config.getNavigationStateQualifier();
    if (navigationStateQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.NAV_STATE_TAG_NAME);
      node.setTextContent(navigationStateQualifier.getFolderSegment());
    }

    final NavigationMethodQualifier navigationMethodQualifier = config.getNavigationMethodQualifier();
    if (navigationMethodQualifier != null) {
      Element node = createNode(doc, configNode, LayoutDeviceHandler.NAV_METHOD_TAG_NAME);
      node.setTextContent(navigationMethodQualifier.getFolderSegment());
    }

    final ScreenDimensionQualifier screenDimensionQualifier = config.getScreenDimensionQualifier();
    if (screenDimensionQualifier != null) {
      final Element screenDimensionNode = createNode(doc, configNode, LayoutDeviceHandler.SCREEN_DIMENSION_TAG_NAME);

      final Element size1Node = createNode(doc, screenDimensionNode, LayoutDeviceHandler.SIZE_TAG_NAME);
      size1Node.setTextContent(Integer.toString(screenDimensionQualifier.getValue1()));

      final Element size2Node = createNode(doc, screenDimensionNode, LayoutDeviceHandler.SIZE_TAG_NAME);
      size2Node.setTextContent(Integer.toString(screenDimensionQualifier.getValue2()));
    }
  }

  @NotNull
  public LayoutDeviceConfiguration addConfig(@NotNull String name, @NotNull FolderConfiguration config) {
    synchronized (myConfigurations) {
      final LayoutDeviceConfiguration newConfig = doAddConfig(name, config);
      seal();
      return newConfig;
    }
  }

  void addConfigs(List<LayoutDeviceConfiguration> configs) {
    synchronized (myConfigurations) {
      for (LayoutDeviceConfiguration config : configs) {
        for (LayoutDeviceConfiguration c : myConfigurations) {
          if (c.getName().equals(config.getName())) {
            myConfigurations.remove(c);
            break;
          }
        }
        myConfigurations.add(config);
      }
      seal();
    }
  }

  void removeConfig(String name) {
    synchronized (myConfigurations) {
      for (LayoutDeviceConfiguration config : myConfigurations) {
        if (config.getName().equals(name)) {
          myConfigurations.remove(config);
          seal();
          return;
        }
      }
    }
  }

  @NotNull
  private LayoutDeviceConfiguration doAddConfig(@NotNull String name, @NotNull FolderConfiguration config) {
    for (LayoutDeviceConfiguration c : myConfigurations) {
      if (c.getName().equals(name)) {
        myConfigurations.remove(c);
        break;
      }
    }
    final LayoutDeviceConfiguration newConfiguration = new LayoutDeviceConfiguration(this, name, config);
    myConfigurations.add(newConfiguration);
    return newConfiguration;
  }

  private void seal() {
    mROList = Collections.unmodifiableList(myConfigurations);
  }

  void setXDpi(float xdpi) {
    mXDpi = xdpi;
  }

  void setYDpi(float ydpi) {
    mYDpi = ydpi;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public List<LayoutDeviceConfiguration> getConfigurations() {
    synchronized (myConfigurations) {
      return mROList;
    }
  }

  @Nullable
  public LayoutDeviceConfiguration getDeviceConfigByName(String name) {
    synchronized (myConfigurations) {
      for (LayoutDeviceConfiguration config : myConfigurations) {
        if (config.getName().equals(name)) {
          return config;
        }
      }
    }
    return null;
  }

  @Nullable
  public FolderConfiguration getFolderConfigByName(String name) {
    synchronized (myConfigurations) {
      for (LayoutDeviceConfiguration config : myConfigurations) {
        if (config.getName().equals(name)) {
          return config.getConfiguration();
        }
      }
    }
    return null;
  }

  public float getXDpi() {
    return mXDpi;
  }

  public float getYDpi() {
    return mYDpi;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  static enum Type {
    PLATFORM, ADD_ON, CUSTOM
  }
}
