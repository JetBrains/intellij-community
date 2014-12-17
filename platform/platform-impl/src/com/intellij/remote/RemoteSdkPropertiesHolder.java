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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathMappingSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class RemoteSdkPropertiesHolder implements RemoteSdkProperties {
  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";
  private static final String HELPERS_PATH = "HELPERS_PATH";
  private static final String REMOTE_ROOTS = "REMOTE_ROOTS";
  private static final String REMOTE_PATH = "REMOTE_PATH";
  private static final String INITIALIZED = "INITIALIZED";
  private static final String VALID = "VALID";
  private static final String PATH_MAPPINGS = "PATH_MAPPINGS";

  private String mySdkId;

  private String myInterpreterPath;
  private String myHelpersPath;

  private final String myHelpersDefaultDirName;

  private boolean myHelpersVersionChecked = false;

  private Set<String> myRemoteRoots = Sets.newTreeSet();

  private boolean myInitialized = false;

  private boolean myValid = true;

  @NotNull
  private PathMappingSettings myPathMappings = new PathMappingSettings();

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
    return Lists.newArrayList(myRemoteRoots);
  }

  @Override
  public void setRemoteRoots(List<String> remoteRoots) {
    myRemoteRoots = Sets.newTreeSet(remoteRoots);
  }

  @NotNull
  @Override
  public PathMappingSettings getPathMappings() {
    return myPathMappings;
  }

  @Override
  public void setPathMappings(@Nullable PathMappingSettings pathMappings) {
    myPathMappings = new PathMappingSettings();
    if (pathMappings != null) {
      myPathMappings.addAll(pathMappings);
    }
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

  @Override
  public boolean isValid() {
    return myValid;
  }

  @Override
  public void setValid(boolean valid) {
    myValid = valid;
  }

  public void copyTo(RemoteSdkProperties copy) {
    copy.setInterpreterPath(getInterpreterPath());
    copy.setHelpersPath(getHelpersPath());
    copy.setHelpersVersionChecked(isHelpersVersionChecked());

    copy.setRemoteRoots(getRemoteRoots());

    copy.setInitialized(isInitialized());

    copy.setValid(isValid());
  }

  public void save(Element rootElement) {
    rootElement.setAttribute(INTERPRETER_PATH, StringUtil.notNullize(getInterpreterPath()));
    rootElement.setAttribute(HELPERS_PATH, StringUtil.notNullize(getHelpersPath()));

    rootElement.setAttribute(INITIALIZED, Boolean.toString(isInitialized()));
    rootElement.setAttribute(VALID, Boolean.toString(isValid()));

    PathMappingSettings.writeExternal(rootElement, myPathMappings);

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

    setValid(StringUtil.parseBoolean(element.getAttributeValue(VALID), true));

    setPathMappings(PathMappingSettings.readExternal(element));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteSdkPropertiesHolder holder = (RemoteSdkPropertiesHolder)o;

    if (myHelpersVersionChecked != holder.myHelpersVersionChecked) return false;
    if (myInitialized != holder.myInitialized) return false;
    if (myValid != holder.myValid) return false;
    if (myHelpersDefaultDirName != null
        ? !myHelpersDefaultDirName.equals(holder.myHelpersDefaultDirName)
        : holder.myHelpersDefaultDirName != null) {
      return false;
    }
    if (myHelpersPath != null ? !myHelpersPath.equals(holder.myHelpersPath) : holder.myHelpersPath != null) return false;
    if (myInterpreterPath != null ? !myInterpreterPath.equals(holder.myInterpreterPath) : holder.myInterpreterPath != null) return false;
    if (!myPathMappings.equals(holder.myPathMappings)) return false;
    if (myRemoteRoots != null ? !myRemoteRoots.equals(holder.myRemoteRoots) : holder.myRemoteRoots != null) return false;
    if (mySdkId != null ? !mySdkId.equals(holder.mySdkId) : holder.mySdkId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySdkId != null ? mySdkId.hashCode() : 0;
    result = 31 * result + (myInterpreterPath != null ? myInterpreterPath.hashCode() : 0);
    result = 31 * result + (myHelpersPath != null ? myHelpersPath.hashCode() : 0);
    result = 31 * result + (myHelpersDefaultDirName != null ? myHelpersDefaultDirName.hashCode() : 0);
    result = 31 * result + (myHelpersVersionChecked ? 1 : 0);
    result = 31 * result + (myRemoteRoots != null ? myRemoteRoots.hashCode() : 0);
    result = 31 * result + (myInitialized ? 1 : 0);
    result = 31 * result + (myValid ? 1 : 0);
    result = 31 * result + myPathMappings.hashCode();
    return result;
  }
}
