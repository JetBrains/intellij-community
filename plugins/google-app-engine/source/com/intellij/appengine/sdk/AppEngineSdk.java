package com.intellij.appengine.sdk;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import com.intellij.javaee.appServerIntegrations.ApplicationServer;

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
}
