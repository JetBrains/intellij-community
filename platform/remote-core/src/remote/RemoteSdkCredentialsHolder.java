// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.util.PathMappingSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteSdkCredentialsHolder extends RemoteCredentialsHolder implements RemoteSdkCredentials {
  private final RemoteSdkPropertiesHolder myRemoteSdkProperties;

  public RemoteSdkCredentialsHolder(@NotNull String defaultHelpersDirName) {
    myRemoteSdkProperties = new RemoteSdkPropertiesHolder(defaultHelpersDirName);
  }

  public @NotNull RemoteSdkPropertiesHolder getRemoteSdkProperties() {
    return myRemoteSdkProperties;
  }

  @Override
  public String getInterpreterPath() {
    return myRemoteSdkProperties.getInterpreterPath();
  }

  @Override
  public void setInterpreterPath(String interpreterPath) {
    myRemoteSdkProperties.setInterpreterPath(interpreterPath);
  }

  @Override
  public String getHelpersPath() {
    return myRemoteSdkProperties.getHelpersPath();
  }

  @Override
  public void setHelpersPath(String helpersPath) {
    myRemoteSdkProperties.setHelpersPath(helpersPath);
  }

  @Override
  public String getDefaultHelpersName() {
    return myRemoteSdkProperties.getDefaultHelpersName();
  }

  @Override
  public @NotNull PathMappingSettings getPathMappings() {
    return myRemoteSdkProperties.getPathMappings();
  }

  @Override
  public void setPathMappings(@Nullable PathMappingSettings pathMappings) {
    myRemoteSdkProperties.setPathMappings(pathMappings);
  }

  @Override
  public boolean isHelpersVersionChecked() {
    return myRemoteSdkProperties.isHelpersVersionChecked();
  }

  @Override
  public void setHelpersVersionChecked(boolean helpersVersionChecked) {
    myRemoteSdkProperties.setHelpersVersionChecked(helpersVersionChecked);
  }

  @Override
  public @NotNull String getFullInterpreterPath() {
    return RemoteSdkProperties.getFullInterpreterPath(this, myRemoteSdkProperties);
  }

  @Override
  public void setSdkId(String sdkId) {
    myRemoteSdkProperties.setSdkId(sdkId);
  }

  @Override
  public String getSdkId() {
    return myRemoteSdkProperties.getSdkId();
  }

  @Override
  public boolean isValid() {
    return myRemoteSdkProperties.isValid();
  }

  @Override
  public void setValid(boolean valid) {
    myRemoteSdkProperties.setValid(valid);
  }

  @Override
  public boolean isRunAsRootViaSudo() {
    return myRemoteSdkProperties.isRunAsRootViaSudo();
  }

  @Override
  public void setRunAsRootViaSudo(boolean runAsRootViaSudo) {
    myRemoteSdkProperties.setRunAsRootViaSudo(runAsRootViaSudo);
  }

  @Override
  public void load(Element element) {
    super.load(element);
    myRemoteSdkProperties.load(element);
  }

  @Override
  public void save(Element rootElement) {
    super.save(rootElement);
    myRemoteSdkProperties.save(rootElement);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteSdkCredentialsHolder holder = (RemoteSdkCredentialsHolder)o;

    if (!getLiteralPort().equals(holder.getLiteralPort())) return false;
    if (isStorePassphrase() != holder.isStorePassphrase()) return false;
    if (isStorePassword() != holder.isStorePassword()) return false;
    if (getAuthType() != holder.getAuthType()) return false;
    if (!getHost().equals(holder.getHost())) return false;
    if (getPassphrase() != null ? !getPassphrase().equals(holder.getPassphrase()) : holder.getPassphrase() != null) return false;
    if (getPassword() != null ? !getPassword().equals(holder.getPassword()) : holder.getPassword() != null) return false;
    if (!getPrivateKeyFile().equals(holder.getPrivateKeyFile())) {
      return false;
    }
    if (getUserName() != null ? !getUserName().equals(holder.getUserName()) : holder.getUserName() != null) return false;

    if (!myRemoteSdkProperties.equals(holder.myRemoteSdkProperties)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = getHost().hashCode();
    result = 31 * result + getLiteralPort().hashCode();
    result = 31 * result + (getUserName() != null ? getUserName().hashCode() : 0);
    result = 31 * result + (getPassword() != null ? getPassword().hashCode() : 0);
    result = 31 * result + getAuthType().hashCode();
    result = 31 * result + getPrivateKeyFile().hashCode();
    result = 31 * result + (getPassphrase() != null ? getPassphrase().hashCode() : 0);
    result = 31 * result + (isStorePassword() ? 1 : 0);
    result = 31 * result + (isStorePassphrase() ? 1 : 0);
    result = 31 * result + myRemoteSdkProperties.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "RemoteSdkDataHolder" +
           "{getHost()='" + getHost() + '\'' +
           ", getLiteralPort()=" + getLiteralPort() +
           ", getUserName()='" + getUserName() + '\'' +
           ", myInterpreterPath='" + getInterpreterPath() + '\'' +
           ", isRunAsRootViaSudo=" + isRunAsRootViaSudo() +
           ", myHelpersPath='" + getHelpersPath() + '\'' +
           '}';
  }

  public void copyRemoteSdkCredentialsTo(RemoteSdkCredentialsHolder to) {
    super.copyRemoteCredentialsTo(to);
    myRemoteSdkProperties.copyTo(to.getRemoteSdkProperties());
  }
}
