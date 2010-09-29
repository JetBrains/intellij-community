package com.intellij.appengine.server.integration;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.javaee.appServerIntegrations.AppServerIntegration;
import com.intellij.javaee.appServerIntegrations.ApplicationServerHelper;
import com.intellij.javaee.appServerIntegrations.ApplicationServerPersistentDataEditor;
import com.intellij.javaee.openapi.ex.AppServerIntegrationsManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineServerIntegration extends AppServerIntegration {
  private final AppEngineServerHelper myServerHelper;

  public static AppEngineServerIntegration getInstance() {
    return AppServerIntegrationsManager.getInstance().getIntegration(AppEngineServerIntegration.class);
  }

  public AppEngineServerIntegration() {
    myServerHelper = new AppEngineServerHelper();
  }

  public Icon getIcon() {
    return AppEngineUtil.APP_ENGINE_ICON;
  }

  public String getPresentableName() {
    return "Google App Engine Dev Server";
  }

  @Override
  public ApplicationServerPersistentDataEditor createNewServerEditor() {
    return myServerHelper.createConfigurable();
  }

  @NotNull
  public String getComponentName() {
    return "AppEngineServerIntegration";
  }

  @Override
  public ApplicationServerHelper getApplicationServerHelper() {
    return myServerHelper;
  }
}
