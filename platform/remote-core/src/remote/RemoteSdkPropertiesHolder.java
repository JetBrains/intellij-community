// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathMappingSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class RemoteSdkPropertiesHolder implements RemoteSdkProperties {
  public static final String DEFAULT_HELPERS_DIR_NAME = ".idea_helpers";

  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";
  private static final String HELPERS_PATH = "HELPERS_PATH";
  private static final String VALID = "VALID";
  private static final String RUN_AS_ROOT_VIA_SUDO = "RUN_AS_ROOT_VIA_SUDO";

  private String mySdkId;
  private String myInterpreterPath;
  private String myHelpersPath;
  private final String myHelpersDefaultDirName;
  private boolean myHelpersVersionChecked = false;
  private boolean myValid = true;
  private boolean myRunAsRootViaSudo = false;
  private PathMappingSettings myPathMappings = new PathMappingSettings();

  public RemoteSdkPropertiesHolder(String helpersDefaultDirName) {
    myHelpersDefaultDirName = helpersDefaultDirName;
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

  @Override
  public @NotNull PathMappingSettings getPathMappings() {
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
    copy.setValid(isValid());
    copy.setRunAsRootViaSudo(isRunAsRootViaSudo());
  }

  public void save(Element rootElement) {
    rootElement.setAttribute(INTERPRETER_PATH, StringUtil.notNullize(getInterpreterPath()));
    rootElement.setAttribute(HELPERS_PATH, StringUtil.notNullize(getHelpersPath()));
    rootElement.setAttribute(VALID, Boolean.toString(isValid()));
    rootElement.setAttribute(RUN_AS_ROOT_VIA_SUDO, Boolean.toString(isRunAsRootViaSudo()));
    PathMappingSettings.writeExternal(rootElement, myPathMappings);
  }

  public void load(Element element) {
    setInterpreterPath(StringUtil.nullize(element.getAttributeValue(INTERPRETER_PATH)));
    setHelpersPath(StringUtil.nullize(element.getAttributeValue(HELPERS_PATH)));
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
    if (myValid != holder.myValid) return false;
    if (!Objects.equals(myHelpersDefaultDirName, holder.myHelpersDefaultDirName)) return false;
    if (!Objects.equals(myHelpersPath, holder.myHelpersPath)) return false;
    if (!Objects.equals(myInterpreterPath, holder.myInterpreterPath)) return false;
    if (myRunAsRootViaSudo != holder.myRunAsRootViaSudo) return false;
    if (!myPathMappings.equals(holder.myPathMappings)) return false;
    if (!Objects.equals(mySdkId, holder.mySdkId)) return false;

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
    result = 31 * result + (myValid ? 1 : 0);
    result = 31 * result + myPathMappings.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "RemoteSdkPropertiesHolder{" +
           "mySdkId='" + mySdkId + '\'' +
           ", myInterpreterPath='" + myInterpreterPath + '\'' +
           ", myHelpersPath='" + myHelpersPath + '\'' +
           ", myHelpersDefaultDirName='" + myHelpersDefaultDirName + '\'' +
           ", myHelpersVersionChecked=" + myHelpersVersionChecked +
           ", myValid=" + myValid +
           ", myRunAsRootViaSudo=" + myRunAsRootViaSudo +
           ", myPathMappings=" + myPathMappings +
           '}';
  }
}
