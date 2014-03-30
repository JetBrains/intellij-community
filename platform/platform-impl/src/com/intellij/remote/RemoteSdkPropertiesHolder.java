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
package com.intellij.remote;

import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class RemoteSdkPropertiesHolder implements RemoteSdkProperties {
  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";
  private static final String HELPERS_PATH = "HELPERS_PATH";
  private static final String REMOTE_ROOTS = "REMOTE_ROOTS";
  private static final String REMOTE_PATH = "REMOTE_PATH";
  private static final String INITIALIZED = "INITIALIZED";

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

  public void setSdkId(String sdkId) {
    mySdkId = sdkId;
  }

  public String getSdkId() {
    return mySdkId;
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public void setInitialized(boolean initialized) {
    myInitialized = initialized;

  }

  public void copyTo(RemoteSdkProperties copy) {
    copy.setInterpreterPath(getInterpreterPath());
    copy.setHelpersPath(getHelpersPath());
    copy.setHelpersVersionChecked(isHelpersVersionChecked());

    copy.setRemoteRoots(getRemoteRoots());

    copy.setInitialized(isInitialized());
  }

  public void save(Element rootElement) {
    rootElement.setAttribute(INTERPRETER_PATH, StringUtil.notNullize(getInterpreterPath()));
    rootElement.setAttribute(HELPERS_PATH, StringUtil.notNullize(getHelpersPath()));

    rootElement.setAttribute(INITIALIZED, Boolean.toString(isInitialized()));

    for (String remoteRoot : getRemoteRoots()) {
      final Element child = new Element(REMOTE_ROOTS);
      child.setAttribute(REMOTE_PATH, remoteRoot);
      rootElement.addContent(child);
    }
  }

  public void load(Element element) {
    setInterpreterPath(StringUtil.nullize(element.getAttributeValue(INTERPRETER_PATH)));
    setHelpersPath(StringUtil.nullize(element.getAttributeValue(HELPERS_PATH)));

    setRemoteRoots(JDOMExternalizer.loadStringsList(element, REMOTE_ROOTS, REMOTE_PATH));

    setInitialized(StringUtil.parseBoolean(element.getAttributeValue(INITIALIZED), true));
  }
}
