package com.intellij.appengine.server.integration;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.javaee.appServerIntegrations.ApplicationServerPersistentData;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AppEngineServerData implements ApplicationServerPersistentData {
  private String mySdkPath;

  public AppEngineServerData(@NotNull String sdkPath) {
    mySdkPath = sdkPath;
  }

  @NotNull 
  public String getSdkPath() {
    return mySdkPath;
  }

  @NotNull
  public AppEngineSdk getSdk() {
    return AppEngineSdkManager.getInstance().findSdk(mySdkPath);
  }

  public void setSdkPath(@NotNull String sdkPath) {
    mySdkPath = sdkPath;
  }

  public void readExternal(Element element) throws InvalidDataException {
    mySdkPath = element.getChildTextTrim("sdk-path");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.addContent(new Element("sdk-path").addContent(mySdkPath));
  }
}
