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
import com.intellij.openapi.util.io.FileUtil;

/**
 * @author nik
 */
public class AppEngineServerStartupPolicy implements JavaCommandLineStartupPolicy {
  /*
  public ScriptHelper createStartupScriptHelper(ProgramRunner runner) {
    return new ScriptHelper() {
      @Override
      public ExecutableObject getDefaultScript(CommonModel commonModel) {
        final GeneralCommandLine commandLine;
        try {
          commandLine = CommandLineBuilder.createFromJavaParameters(createCommandLine(commonModel));
        }
        catch (ExecutionException e) {
          return null;
        }
        return new CommandLineExecutableObject(commandLine.getCommands(), "") {
          @Override
          protected GeneralCommandLine createCommandLine(String[] parameters, Map<String, String> envVariables) {
            final String vmOptions = envVariables.get(JAVA_OPTS_VARIABLE);
            if (!StringUtil.isEmpty(vmOptions) && parameters.length > 0) {
              final List<String> jvmArgs = StringUtil.splitHonorQuotes(vmOptions, ' ');
              List<String> newParameters = new ArrayList<String>();
              newParameters.add(parameters[0]);
              for (String jvmArg : jvmArgs) {
                newParameters.add("--jvm_flag=" + jvmArg);
              }
              newParameters.addAll(Arrays.asList(parameters).subList(1, parameters.length));
              parameters = newParameters.toArray(new String[newParameters.size()]);
              LOG.debug("parameters patched: " + Arrays.toString(parameters));
            }
            return super.createCommandLine(parameters, envVariables);
          }
        };
      }
    };
  }

  public ScriptHelper createShutdownScriptHelper(ProgramRunner runner) {
    return null;
  }

  public EnvironmentHelper getEnvironmentHelper() {
    return new EnvironmentHelper() {
      @Override
      public String getDefaultJavaVmEnvVariableName(CommonModel model) {
        return JAVA_OPTS_VARIABLE;
      }
    };
  }
  */

  public JavaParameters createCommandLine(CommonModel commonModel) throws ExecutionException {
    final AppEngineServerData data = (AppEngineServerData)commonModel.getApplicationServer().getPersistentData();
    final AppEngineSdk sdk = data.getSdk();
    if (!sdk.getToolsApiJarFile().exists()) {
      throw new ExecutionException("Path to App Engine SDK isn't specified");
    }
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.getClassPath().add(sdk.getToolsApiJarFile().getAbsolutePath());
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
