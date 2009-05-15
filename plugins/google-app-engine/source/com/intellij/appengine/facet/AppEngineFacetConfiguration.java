package com.intellij.appengine.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author nik
 */
public class AppEngineFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AppEngineFacetConfiguration> {
  private String mySdkHomePath = "";
  private boolean myRunEnhancerOnMake = false;

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
       new AppEngineFacetEditor(this, editorContext, validatorsManager)
    };
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public void writeExternal(Element element) throws WriteExternalException {
  }

  public AppEngineFacetConfiguration getState() {
    return this;
  }

  public void loadState(AppEngineFacetConfiguration state) {
    //todo[nik] remove toSystemIndependentName call later. It is needed only to fix incorrect config files
    mySdkHomePath = FileUtil.toSystemIndependentName(state.getSdkHomePath());
    myRunEnhancerOnMake = state.isRunEnhancerOnMake();
  }

  @Tag("sdk-home-path")
  public String getSdkHomePath() {
    return mySdkHomePath;
  }

  public void setSdkHomePath(String sdkHomePath) {
    mySdkHomePath = sdkHomePath;
  }

  @Tag("run-enhancer-on-make")
  public boolean isRunEnhancerOnMake() {
    return myRunEnhancerOnMake;
  }

  public void setRunEnhancerOnMake(boolean runEnhancerOnMake) {
    myRunEnhancerOnMake = runEnhancerOnMake;
  }
}
