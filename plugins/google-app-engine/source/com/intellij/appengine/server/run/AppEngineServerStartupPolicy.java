package com.intellij.appengine.server.run;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.instance.AppEngineServerModel;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.configuration.JavaCommandLineStartupPolicy;
import com.intellij.javaee.run.configuration.ServerModel;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

/**
 * @author nik
 */
public class AppEngineServerStartupPolicy implements JavaCommandLineStartupPolicy {
  public JavaParameters createCommandLine(CommonModel commonModel) throws ExecutionException {
    final AppEngineServerData data = (AppEngineServerData)commonModel.getApplicationServer().getPersistentData();
    final AppEngineSdk sdk = data.getSdk();
    if (StringUtil.isEmpty(sdk.getSdkHomePath())) {
      throw new ExecutionException("Path to App Engine SDK isn't specified");
    }
    final File toolsApiJarFile = sdk.getToolsApiJarFile();
    if (!toolsApiJarFile.exists()) {
      throw new ExecutionException("'" + sdk.getSdkHomePath() + "' isn't valid App Engine SDK installation: '" + toolsApiJarFile.getAbsolutePath() + "' not found"); 
    }
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.getClassPath().add(toolsApiJarFile.getAbsolutePath());
    javaParameters.setMainClass("com.google.appengine.tools.development.DevAppServerMain");

    final ServerModel serverModel = commonModel.getServerModel();
    final WebFacet webFacet = ((AppEngineServerModel)serverModel).getWebFacet();
    if (webFacet == null) {
      throw new ExecutionException("Web Facet isn't specified");
    }
    final Sdk jdk = ModuleRootManager.getInstance(webFacet.getModule()).getSdk();
    if (jdk == null) {
      throw new ExecutionException("JDK isn't specified for module '" + webFacet.getModule().getName() + "'");
    }
    javaParameters.setJdk(jdk);

    final ParametersList parameters = javaParameters.getProgramParametersList();
    parameters.add("-p", String.valueOf(serverModel.getLocalPort()));
    parameters.add("--disable_update_check");

    final BuildConfiguration buildProperties = webFacet.getBuildConfiguration().getBuildProperties();
    final String explodedPath = buildProperties.getExplodedPath();
    if (!buildProperties.isExplodedEnabled() || explodedPath == null) {
      throw new ExecutionException("Exploded directory isn't specified for '" + webFacet.getName() + "' Facet");
    }
    final String explodedPathParameter = FileUtil.toSystemDependentName(explodedPath);
    parameters.add(explodedPathParameter);
    javaParameters.setWorkingDirectory(explodedPathParameter);
    if (SystemInfo.isMac) {
      javaParameters.getVMParametersList().add("-XstartOnFirstThread");
    }
    return javaParameters;
  }
}
