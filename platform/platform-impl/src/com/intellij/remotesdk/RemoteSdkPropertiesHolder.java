/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remotesdk;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class RemoteSdkPropertiesHolder implements RemoteSdkProperties {
  private String mySdkId;

  private String myInterpreterPath;
  private String myHelpersPath;

  private final String myHelpersDefaultDirName;

  private boolean myHelpersVersionChecked = false;

  private List<String> myRemoteRoots = new ArrayList<String>();

  private boolean myInitialized = false;

  public RemoteSdkPropertiesHolder(String name) {
    myHelpersDefaultDirName = name;
  }

  @Override
  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  @Override
  public void setInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }

  @Override
  public String getHelpersPath() {
    return myHelpersPath;
  }

  @Override
  public void setHelpersPath(String helpersPath) {
    myHelpersPath = helpersPath;
  }

  public String getDefaultHelpersName() {
    return myHelpersDefaultDirName;
  }

  @Override
  public void addRemoteRoot(String remoteRoot) {
    myRemoteRoots.add(remoteRoot);
  }

  @Override
  public void clearRemoteRoots() {
    myRemoteRoots.clear();
  }

  @Override
  public List<String> getRemoteRoots() {
    return myRemoteRoots;
  }

  @Override
  public void setRemoteRoots(List<String> remoteRoots) {
    myRemoteRoots = remoteRoots;
  }

  @Override
  public boolean isHelpersVersionChecked() {
    return myHelpersVersionChecked;
  }

  @Override
  public void setHelpersVersionChecked(boolean helpersVersionChecked) {
    myHelpersVersionChecked = helpersVersionChecked;
  }

  @Override
  public String getFullInterpreterPath() {
    return mySdkId;
  }

  public void setSdkId(String sdkId) {
    mySdkId = sdkId;
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public void setInitialized(boolean initialized) {
    myInitialized = initialized;

  }

  public void copyTo(RemoteSdkPropertiesHolder copy) {
    copy.setInterpreterPath(getInterpreterPath());
    copy.setHelpersPath(getHelpersPath());
    copy.setHelpersVersionChecked(isHelpersVersionChecked());

    copy.setRemoteRoots(getRemoteRoots());

    copy.setInitialized(isInitialized());
  }
}
