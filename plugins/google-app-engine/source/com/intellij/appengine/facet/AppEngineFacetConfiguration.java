/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.appengine.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.appengine.model.impl.AppEngineModuleExtensionProperties;

import java.util.List;

/**
 * @author nik
 */
public class AppEngineFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AppEngineModuleExtensionProperties> {
  private AppEngineModuleExtensionProperties myProperties = new AppEngineModuleExtensionProperties();

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
       new AppEngineFacetEditor(this, editorContext, validatorsManager)
    };
  }

  public AppEngineModuleExtensionProperties getState() {
    return myProperties;
  }

  public void loadState(AppEngineModuleExtensionProperties state) {
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
