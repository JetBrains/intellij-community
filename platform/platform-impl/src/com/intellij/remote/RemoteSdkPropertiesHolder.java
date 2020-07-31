// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathMappingSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

public class RemoteSdkPropertiesHolder implements RemoteSdkProperties {
  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";
  private static final String HELPERS_PATH = "HELPERS_PATH";
  private static final String REMOTE_ROOTS = "REMOTE_ROOTS";
  private static final String REMOTE_PATH = "REMOTE_PATH";
  private static final String INITIALIZED = "INITIALIZED";
  private static final String VALID = "VALID";
  private static final String PATH_MAPPINGS = "PATH_MAPPINGS";
  private static final String RUN_AS_ROOT_VIA_SUDO = "RUN_AS_ROOT_VIA_SUDO";

  private String mySdkId;

  private String myInterpreterPath;
  private String myHelpersPath;

  private final String myHelpersDefaultDirName;

  private boolean myHelpersVersionChecked = false;

  private Set<String> myRemoteRoots = new TreeSet<String>();

  private boolean myInitialized = false;

  private boolean myValid = true;

  private boolean myRunAsRootViaSudo = false;

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

  @Override
  public String getDefaultHelpersName() {
    return myHelpersDefaultDirName;
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

  @Override
  public void setSdkId(String sdkId) {
    mySdkId = sdkId;
  }

  @Override
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

  @Override
  public boolean isRunAsRootViaSudo() {
    return myRunAsRootViaSudo;
  }

  @Override
  public void setRunAsRootViaSudo(boolean runAsRootViaSudo) {
    myRunAsRootViaSudo = runAsRootViaSudo;
  }

  public void copyTo(RemoteSdkProperties copy) {
    copy.setInterpreterPath(getInterpreterPath());
    copy.setHelpersPath(getHelpersPath());
    copy.setHelpersVersionChecked(isHelpersVersionChecked());

    copy.setInitialized(isInitialized());

    copy.setValid(isValid());

    copy.setRunAsRootViaSudo(isRunAsRootViaSudo());
  }

  public void save(Element rootElement) {
    rootElement.setAttribute(INTERPRETER_PATH, StringUtil.notNullize(getInterpreterPath()));
    rootElement.setAttribute(HELPERS_PATH, StringUtil.notNullize(getHelpersPath()));

    rootElement.setAttribute(INITIALIZED, Boolean.toString(isInitialized()));
    rootElement.setAttribute(VALID, Boolean.toString(isValid()));
    rootElement.setAttribute(RUN_AS_ROOT_VIA_SUDO, Boolean.toString(isRunAsRootViaSudo()));

    PathMappingSettings.writeExternal(rootElement, myPathMappings);
  }

  public void load(Element element) {
    setInterpreterPath(StringUtil.nullize(element.getAttributeValue(INTERPRETER_PATH)));
    setHelpersPath(StringUtil.nullize(element.getAttributeValue(HELPERS_PATH)));

    setInitialized(Boolean.parseBoolean(element.getAttributeValue(INITIALIZED)));

    setValid(Boolean.parseBoolean(element.getAttributeValue(VALID)));

    setPathMappings(PathMappingSettings.readExternal(element));

    setRunAsRootViaSudo(Boolean.parseBoolean(element.getAttributeValue(RUN_AS_ROOT_VIA_SUDO)));
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
    if (myRunAsRootViaSudo != holder.myRunAsRootViaSudo) return false;
    if (!myPathMappings.equals(holder.myPathMappings)) return false;
    if (myRemoteRoots != null ? !myRemoteRoots.equals(holder.myRemoteRoots) : holder.myRemoteRoots != null) return false;
    if (mySdkId != null ? !mySdkId.equals(holder.mySdkId) : holder.mySdkId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySdkId != null ? mySdkId.hashCode() : 0;
    result = 31 * result + (myInterpreterPath != null ? myInterpreterPath.hashCode() : 0);
    result = 31 * result + (myRunAsRootViaSudo ? 1 : 0);
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
