package com.intellij.appengine.sdk;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.openapi.vfs.VirtualFile;
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

  VirtualFile[] getOrmLibSources();

  File getWebSchemeFile();

  File[] getJspLibraries();

  void patchJavaParametersForDevServer(ParametersList vmParameters);
}
