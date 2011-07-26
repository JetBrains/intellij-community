package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.*;
import com.android.resources.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class LayoutDeviceHandler extends DefaultHandler {
  private static final String DEVICE_TAG_NAME = "device";
  private static final String CONFIG_TAG_NAME = "config";
  private static final String DEFAULT_TAG_NAME = "default";

  static final String SCREEN_DIMENSION_TAG_NAME = "screen-dimension";
  static final String COUNTRY_CODE_TAG_NAME = "country-code";
  static final String NETWORK_CODE_TAG_NAME = "network-code";
  static final String SCREEN_SIZE_TAG_NAME = "screen-size";
  static final String SCREEN_RATIO_TAG_NAME = "screen-ratio";
  static final String SCREEN_ORIENTATION_TAG_NAME = "screen-orientation";
  static final String PIXEL_DENSITY_TAG_NAME = "pixel-density";
  static final String TOUCH_TYPE_TAG_NAME = "touch-type";
  static final String KEYBOARD_STATE_TAG_NAME = "keyboard-state";
  static final String TEXT_INPUT_METHOD_TAG_NAME = "text-input-method";
  static final String NAV_STATE_TAG_NAME = "nav-state";
  static final String NAV_METHOD_TAG_NAME = "nav-method";
  static final String XDPI_TAG_NAME = "xdpi";
  static final String YDPI_TAG_NAME = "ydpi";
  static final String SIZE_TAG_NAME = "size";
  static final String NAME_ATTRIBUTE = "name";

  private final List<LayoutDevice> myDevices = new ArrayList<LayoutDevice>();
  private final StringBuilder myStringBuilder = new StringBuilder();

  private LayoutDevice myCurrentDevice;
  private FolderConfiguration myDefaultConfiguration;
  private FolderConfiguration myCurrentConfiguration;
  private String mySize1;
  private String mySize2;

  public List<LayoutDevice> getDevices() {
    return myDevices;
  }

  @Override
  public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
    if (DEVICE_TAG_NAME.equals(localName)) {
      final String deviceName = attributes.getValue("", NAME_ATTRIBUTE);
      if (deviceName != null) {
        myCurrentDevice = new LayoutDevice(deviceName);
        myDevices.add(myCurrentDevice);
      }
    }
    else if (DEFAULT_TAG_NAME.equals(localName)) {
      myDefaultConfiguration = myCurrentConfiguration = new FolderConfiguration();
    }
    else if (CONFIG_TAG_NAME.equals(localName)) {
      myCurrentConfiguration = new FolderConfiguration();

      if (myDefaultConfiguration != null) {
        myCurrentConfiguration.set(myDefaultConfiguration);
      }
      final String deviceName = attributes.getValue("", NAME_ATTRIBUTE);
      if (deviceName != null) {
        myCurrentDevice.addConfig(deviceName, myCurrentConfiguration);
      }
    }
    else if (SCREEN_DIMENSION_TAG_NAME.equals(localName)) {
      mySize1 = null;
      mySize2 = null;
    }
    myStringBuilder.setLength(0);
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    myStringBuilder.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String name) throws SAXException {
    if (DEVICE_TAG_NAME.equals(localName)) {
      myCurrentDevice = null;
      myDefaultConfiguration = null;
    }
    else if (CONFIG_TAG_NAME.equals(localName)) {
      //mCurrentConfig.updateScreenWidthAndHeight();
      myCurrentConfiguration = null;
    }
    else if (COUNTRY_CODE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setCountryCodeQualifier(new CountryCodeQualifier(
        Integer.parseInt(myStringBuilder.toString())));
    }
    else if (NETWORK_CODE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setNetworkCodeQualifier(new NetworkCodeQualifier(
        Integer.parseInt(myStringBuilder.toString())));
    }
    else if (SCREEN_SIZE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setScreenSizeQualifier(new ScreenSizeQualifier(
        ScreenSize.getEnum(myStringBuilder.toString())));
    }
    else if (SCREEN_RATIO_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setScreenRatioQualifier(new ScreenRatioQualifier(
        ScreenRatio.getEnum(myStringBuilder.toString())));
    }
    else if (SCREEN_ORIENTATION_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setScreenOrientationQualifier(new ScreenOrientationQualifier(
        ScreenOrientation.getEnum(myStringBuilder.toString())));
    }
    else if (PIXEL_DENSITY_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setPixelDensityQualifier(new PixelDensityQualifier(
        Density.getEnum(myStringBuilder.toString())));
    }
    else if (TOUCH_TYPE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setTouchTypeQualifier(new TouchScreenQualifier(
        TouchScreen.getEnum(myStringBuilder.toString())));
    }
    else if (KEYBOARD_STATE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setKeyboardStateQualifier(new KeyboardStateQualifier(
        KeyboardState.getEnum(myStringBuilder.toString())));
    }
    else if (TEXT_INPUT_METHOD_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setTextInputMethodQualifier(new TextInputMethodQualifier(
        Keyboard.getEnum(myStringBuilder.toString())));
    }
    else if (NAV_STATE_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setNavigationStateQualifier(new NavigationStateQualifier(
        NavigationState.getEnum(myStringBuilder.toString())));
    }
    else if (NAV_METHOD_TAG_NAME.equals(localName)) {
      myCurrentConfiguration.setNavigationMethodQualifier(new NavigationMethodQualifier(
        Navigation.getEnum(myStringBuilder.toString())));
    }
    else if (SCREEN_DIMENSION_TAG_NAME.equals(localName)) {
      final ScreenDimensionQualifier qualifier = ScreenDimensionQualifier.getQualifier(mySize1, mySize2);
      if (qualifier != null) {
        myCurrentConfiguration.setScreenDimensionQualifier(qualifier);
      }
    }
    else if (XDPI_TAG_NAME.equals(localName)) {
      myCurrentDevice.setXDpi(Float.parseFloat(myStringBuilder.toString()));
    }
    else if (YDPI_TAG_NAME.equals(localName)) {
      myCurrentDevice.setYDpi(Float.parseFloat(myStringBuilder.toString()));
    }
    else if (SIZE_TAG_NAME.equals(localName)) {
      if (mySize1 == null) {
        mySize1 = myStringBuilder.toString();
      }
      else if (mySize2 == null) {
        mySize2 = myStringBuilder.toString();
      }
    }
  }
}
