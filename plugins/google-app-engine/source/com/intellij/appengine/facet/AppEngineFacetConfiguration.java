// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.appengine.model.impl.AppEngineModuleExtensionProperties;

import java.util.List;

/**
 * @author nik
 */
public class AppEngineFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AppEngineModuleExtensionProperties> {
  private AppEngineModuleExtensionProperties myProperties = new AppEngineModuleExtensionProperties();

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
       new AppEngineFacetEditor(this, editorContext, validatorsManager)
    };
  }

  @Override
  public AppEngineModuleExtensionProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(@NotNull AppEngineModuleExtensionProperties state) {
    myProperties = state;
  }

  public String getSdkHomePath() {
    return myProperties.mySdkHomePath;
  }

  public boolean isRunEnhancerOnMake() {
    return myProperties.myRunEnhancerOnMake;
  }

  public List<String> getFilesToEnhance() {
    return myProperties.myFilesToEnhance;
  }

  public PersistenceApi getPersistenceApi() {
    return myProperties.myPersistenceApi;
  }

  public void setSdkHomePath(String sdkHomePath) {
    myProperties.mySdkHomePath = sdkHomePath;
  }

  public void setPersistenceApi(PersistenceApi persistenceApi) {
    myProperties.myPersistenceApi = persistenceApi;
  }

  public void setFilesToEnhance(List<String> filesToEnhance) {
    myProperties.myFilesToEnhance = filesToEnhance;
  }

  public void setRunEnhancerOnMake(boolean runEnhancerOnMake) {
    myProperties.myRunEnhancerOnMake = runEnhancerOnMake;
  }
}
