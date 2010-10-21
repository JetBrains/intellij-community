package com.intellij.appengine.server.run;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.instance.AppEngineServerModel;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.configuration.JavaCommandLineStartupPolicy;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;

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

    final AppEngineServerModel serverModel = (AppEngineServerModel) commonModel.getServerModel();
    final Artifact artifact = serverModel.getArtifact();
    if (artifact == null) {
      throw new ExecutionException("Artifact isn't specified");
    }
    final Sdk jdk = ProjectRootManager.getInstance(commonModel.getProject()).getProjectSdk();
    if (jdk == null) {
      throw new ExecutionException("JDK isn't specified for the project");
    }
    javaParameters.setJdk(jdk);

    final ParametersList parameters = javaParameters.getProgramParametersList();
    parameters.addParametersString(serverModel.getServerParameters());
    parameters.replaceOrAppend("-p", "");
    parameters.replaceOrAppend("--port", "");
    parameters.add("-p", String.valueOf(serverModel.getLocalPort()));
    parameters.add("--disable_update_check");

    final String outputPath = artifact.getOutputPath();
    if (outputPath == null || outputPath.length() == 0) {
      throw new ExecutionException("Output path isn't specified for '" + artifact.getName() + "' artifact");
    }
    final String explodedPathParameter = FileUtil.toSystemDependentName(outputPath);
    parameters.add(explodedPathParameter);
    javaParameters.setWorkingDirectory(explodedPathParameter);
    final ParametersList vmParameters = javaParameters.getVMParametersList();
    final String agentPath = sdk.getAgentPath();
    if (new File(FileUtil.toSystemDependentName(agentPath)).exists()) {
      vmParameters.add("-javaagent:" + agentPath);
    }
    if (SystemInfo.isMac) {
      vmParameters.add("-XstartOnFirstThread");
    }
    return javaParameters;
  }

}
