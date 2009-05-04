package com.intellij.appengine.server.integration;

import com.intellij.facet.FacetTypeId;
import com.intellij.javaee.appServerIntegrations.AppServerIntegration;
import com.intellij.javaee.appServerIntegrations.ApplicationServerHelper;
import com.intellij.javaee.facet.JavaeeFacet;
import com.intellij.javaee.facet.JavaeeFacetUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.openapi.ex.AppServerIntegrationsManager;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class AppEngineServerIntegration extends AppServerIntegration {
  private static final Icon APP_ENGINE_ICON = IconLoader.getIcon("/icons/appEngine.png");
  private final AppEngineServerHelper myServerHelper;

  public static AppEngineServerIntegration getInstance() {
    return AppServerIntegrationsManager.getInstance().getIntegration(AppEngineServerIntegration.class);
  }

  public AppEngineServerIntegration() {
    myServerHelper = new AppEngineServerHelper();
  }

  @Override
  public Icon getIcon() {
    return APP_ENGINE_ICON;
  }

  public String getPresentableName() {
    return "Google App Engine Dev Server";
  }

  @NotNull
  public String getComponentName() {
    return "AppEngineServerIntegration";
  }

  @Override
  public ApplicationServerHelper getApplicationServerHelper() {
    return myServerHelper;
  }

  @NotNull
  @Override
  public Collection<FacetTypeId<? extends JavaeeFacet>> getSupportedFacetTypes() {
    return JavaeeFacetUtil.getInstance().getSingletonCollection(WebFacet.ID);
  }
}
