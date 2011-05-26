package com.intellij.appengine.gwt;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.run.GwtDevModeServer;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * @author nik
 */
public class AppEngineGwtServer extends GwtDevModeServer {
  private final ApplicationServer myServer;

  public AppEngineGwtServer(@NotNull ApplicationServer server) {
    super("app-engine:" + server.getName(), server.getName());
    myServer = server;
  }

  @Override
  public Icon getIcon() {
    return AppEngineUtil.APP_ENGINE_ICON;
  }

  @Override
  public void patchParameters(@NotNull JavaParameters parameters, @NotNull GwtFacet gwtFacet) {
    final ParametersList programParameters = parameters.getProgramParametersList();
    programParameters.add("-server");
    programParameters.add("com.google.appengine.tools.development.gwt.AppEngineLauncher");

    final AppEngineSdk sdk = ((AppEngineServerData)myServer.getPersistentData()).getSdk();
    sdk.patchJavaParametersForDevServer(parameters.getVMParametersList());

    //actually these jars are added by AppEngine dev server automatically. But they need to be added to classpath before gwt-dev.jar, because
    // otherwise wrong jsp compiler version will be used (see IDEA-63068)
    for (File jar : ArrayUtil.mergeArrays(sdk.getLibraries(), sdk.getJspLibraries(), File.class)) {
      parameters.getClassPath().addFirst(FileUtil.toSystemIndependentName(jar.getAbsolutePath()));
    }

    parameters.getClassPath().add(sdk.getToolsApiJarFile());
  }
}
