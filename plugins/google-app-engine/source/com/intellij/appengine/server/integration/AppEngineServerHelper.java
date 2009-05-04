package com.intellij.appengine.server.integration;

import com.intellij.javaee.appServerIntegrations.*;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class AppEngineServerHelper implements ApplicationServerHelper {
  public ApplicationServerInfo getApplicationServerInfo(ApplicationServerPersistentData persistentData)
      throws CantFindApplicationServerJarsException {
    File sdkHome = new File(FileUtil.toSystemDependentName(((AppEngineServerData)persistentData).getSdkPath()));
    final File libFolder = new File(sdkHome, "lib" + File.separator + "shared");
    List<File> jars = new ArrayList<File>();
    final File[] files = libFolder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          jars.add(file);
        }
      }
    }

    return new ApplicationServerInfo(jars.toArray(new File[jars.size()]), "AppEngine Dev");
  }

  public ApplicationServerPersistentData createPersistentDataEmptyInstance() {
    return new AppEngineServerData("");
  }

  public ApplicationServerPersistentDataEditor createConfigurable() {
    return new AppEngineServerEditor();
  }
}
