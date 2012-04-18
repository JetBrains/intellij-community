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

import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Alexander Lobas
 */
@Tag("profile")
public class Profile {
  public static final String FULL = "[Full]";

  protected String myName = FULL;

  private boolean myShowDevice = true;
  private boolean myShowDeviceConfiguration = true;
  private boolean myShowTarget = true;
  private boolean myShowLocale = true;
  private boolean myShowDockMode = true;
  private boolean myShowNightMode = true;
  private boolean myShowTheme = true;

  protected String myDevice;
  protected String myDeviceConfiguration;
  protected String myTargetHashString;
  protected String myLocaleLanguage;
  protected String myLocaleRegion;
  protected String myDockMode;
  protected String myNightMode;
  protected String myTheme;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public boolean isShowDevice() {
    return myShowDevice;
  }

  public void setShowDevice(boolean showDevice) {
    myShowDevice = showDevice;
  }

  public boolean isShowDeviceConfiguration() {
    return myShowDeviceConfiguration;
  }

  public void setShowDeviceConfiguration(boolean showDeviceConfiguration) {
    myShowDeviceConfiguration = showDeviceConfiguration;
  }

  public boolean isShowTarget() {
    return myShowTarget;
  }

  public void setShowTarget(boolean showTarget) {
    myShowTarget = showTarget;
  }

  public boolean isShowLocale() {
    return myShowLocale;
  }

  public void setShowLocale(boolean showLocale) {
    myShowLocale = showLocale;
  }

  public boolean isShowDockMode() {
    return myShowDockMode;
  }

  public void setShowDockMode(boolean showDockMode) {
    myShowDockMode = showDockMode;
  }

  public boolean isShowNightMode() {
    return myShowNightMode;
  }

  public void setShowNightMode(boolean showNightMode) {
    myShowNightMode = showNightMode;
  }

  public boolean isShowTheme() {
    return myShowTheme;
  }

  public void setShowTheme(boolean showTheme) {
    myShowTheme = showTheme;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public String getDevice() {
    return myDevice;
  }

  public void setDevice(String device) {
    myDevice = device;
  }

  public String getDeviceConfiguration() {
    return myDeviceConfiguration;
  }

  public void setDeviceConfiguration(String deviceConfiguration) {
    myDeviceConfiguration = deviceConfiguration;
  }

  public String getTargetHashString() {
    return myTargetHashString;
  }

  public void setTargetHashString(String targetHashString) {
    myTargetHashString = targetHashString;
  }

  public String getDockMode() {
    return myDockMode;
  }

  public String getLocaleLanguage() {
    return myLocaleLanguage;
  }

  public void setLocaleLanguage(String localeLanguage) {
    myLocaleLanguage = localeLanguage;
  }

  public String getLocaleRegion() {
    return myLocaleRegion;
  }

  public void setLocaleRegion(String localeRegion) {
    myLocaleRegion = localeRegion;
  }

  public void setDockMode(String dockMode) {
    myDockMode = dockMode;
  }

  public String getNightMode() {
    return myNightMode;
  }

  public void setNightMode(String nightMode) {
    myNightMode = nightMode;
  }

  public String getTheme() {
    return myTheme;
  }

  public void setTheme(String theme) {
    myTheme = theme;
  }
}