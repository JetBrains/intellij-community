package com.intellij.appengine.server.integration;

import com.intellij.javaee.appServerIntegrations.ApplicationServerPersistentData;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author nik
 */
public class AppEngineServerData implements ApplicationServerPersistentData {
  private String mySdkPath;

  public AppEngineServerData(String sdkPath) {
    mySdkPath = sdkPath;
  }

  public String getSdkPath() {
    return mySdkPath;
  }

  public void setSdkPath(String sdkPath) {
    mySdkPath = sdkPath;
  }

  public void readExternal(Element element) throws InvalidDataException {
    mySdkPath = element.getChildTextTrim("sdk-path");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.addContent(new Element("sdk-path").addContent(mySdkPath));
  }
}
