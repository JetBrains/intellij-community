package com.intellij.appengine.sdk;

import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public interface AppEngineSdk {

  @NotNull
  String getSdkHomePath();

  File getAppCfgFile();

  File getToolsApiJarFile();

  File[] getLibraries();

  boolean isClassInWhiteList(@NotNull String className);

  boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName);

  boolean isValid();

  ApplicationServer getOrCreateAppServer();

  String getOrmLibDirectoryPath();

  String getLibUserDirectoryPath();

  File getOrmLibSourcesDirectory();
}
