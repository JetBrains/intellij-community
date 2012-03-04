/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.profile;

/**
 * @author Alexander Lobas
 */
public class ProfileWrapper extends Profile {
  private final Profile myProfile;

  private Boolean myShowDevice;
  private Boolean myShowDeviceConfiguration;
  private Boolean myShowTarget;
  private Boolean myShowLocale;
  private Boolean myShowDockMode;
  private Boolean myShowNightMode;
  private Boolean myShowTheme;

  public ProfileWrapper(Profile profile) {
    myProfile = profile;
    setName(null);
  }

  @Override
  public boolean isShowDevice() {
    return getValue(myShowDevice, myProfile.isShowDevice());
  }

  @Override
  public void setShowDevice(boolean showDevice) {
    myShowDevice = showDevice;
  }

  @Override
  public boolean isShowDeviceConfiguration() {
    return getValue(myShowDeviceConfiguration, myProfile.isShowDeviceConfiguration());
  }

  @Override
  public void setShowDeviceConfiguration(boolean showDeviceConfiguration) {
    myShowDeviceConfiguration = showDeviceConfiguration;
  }

  @Override
  public boolean isShowTarget() {
    return getValue(myShowTarget, myProfile.isShowTarget());
  }

  @Override
  public void setShowTarget(boolean showTarget) {
    myShowTarget = showTarget;
  }

  @Override
  public boolean isShowLocale() {
    return getValue(myShowLocale, myProfile.isShowLocale());
  }

  @Override
  public void setShowLocale(boolean showLocale) {
    myShowLocale = showLocale;
  }

  @Override
  public boolean isShowDockMode() {
    return getValue(myShowDockMode, myProfile.isShowDockMode());
  }

  @Override
  public void setShowDockMode(boolean showDockMode) {
    myShowDockMode = showDockMode;
  }

  @Override
  public boolean isShowNightMode() {
    return getValue(myShowNightMode, myProfile.isShowNightMode());
  }

  @Override
  public void setShowNightMode(boolean showNightMode) {
    myShowNightMode = showNightMode;
  }

  @Override
  public boolean isShowTheme() {
    return getValue(myShowTheme, myProfile.isShowTheme());
  }

  @Override
  public void setShowTheme(boolean showTheme) {
    myShowTheme = showTheme;
  }

  @Override
  public String getName() {
    return getValue(super.getName(), myProfile.getName());
  }

  @Override
  public String getDevice() {
    return getValue(myDevice, myProfile.getDevice());
  }

  @Override
  public String getDeviceConfiguration() {
    return getValue(myDeviceConfiguration, myProfile.getDeviceConfiguration());
  }

  @Override
  public String getTargetHashString() {
    return getValue(myTargetHashString, myProfile.getTargetHashString());
  }

  @Override
  public String getLocaleLanguage() {
    return getValue(myLocaleLanguage, myProfile.getLocaleLanguage());
  }

  @Override
  public String getLocaleRegion() {
    return getValue(myLocaleRegion, myProfile.getLocaleRegion());
  }

  @Override
  public String getDockMode() {
    return getValue(myDockMode, myProfile.getDockMode());
  }

  @Override
  public String getNightMode() {
    return getValue(myNightMode, myProfile.getNightMode());
  }

  @Override
  public String getTheme() {
    return getValue(myTheme, myProfile.getTheme());
  }

  public Profile unwrap() {
    if (myShowDevice != null) {
      myProfile.setShowDevice(myShowDevice);
    }
    if (myShowDeviceConfiguration != null) {
      myProfile.setShowDeviceConfiguration(myShowDeviceConfiguration);
    }
    if (myShowTarget != null) {
      myProfile.setShowTarget(myShowTarget);
    }
    if (myShowLocale != null) {
      myProfile.setShowLocale(myShowLocale);
    }
    if (myShowDockMode != null) {
      myProfile.setShowDockMode(myShowDockMode);
    }
    if (myShowNightMode != null) {
      myProfile.setShowNightMode(myShowNightMode);
    }
    if (myShowTheme != null) {
      myProfile.setShowTheme(myShowTheme);
    }
    if (myName != null) {
      myProfile.setName(myName);
    }
    if (myDevice != null) {
      myProfile.setDevice(myDevice);
    }
    if (myDeviceConfiguration != null) {
      myProfile.setDeviceConfiguration(myDeviceConfiguration);
    }
    if (myTargetHashString != null) {
      myProfile.setTargetHashString(myTargetHashString);
    }
    if (myLocaleLanguage != null) {
      myProfile.setLocaleLanguage(myLocaleLanguage);
    }
    if (myLocaleRegion != null) {
      myProfile.setLocaleRegion(myLocaleRegion);
    }
    if (myDockMode != null) {
      myProfile.setDockMode(myDockMode);
    }
    if (myNightMode != null) {
      myProfile.setNightMode(myNightMode);
    }
    if (myTheme != null) {
      myProfile.setTheme(myTheme);
    }

    return myProfile;
  }

  private static String getValue(String value1, String value2) {
    return value1 == null ? value2 : value1;
  }

  public static boolean getValue(Boolean value1, boolean value2) {
    return value1 == null ? value2 : value1;
  }
}