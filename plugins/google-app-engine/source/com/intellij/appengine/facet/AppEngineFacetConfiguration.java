package com.intellij.appengine.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author nik
 */
public class AppEngineFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AppEngineFacetConfiguration> {
  private String mySdkHomePath = "";

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
    mySdkHomePath = state.getSdkHomePath();
  }

  @Tag("sdk-home-path")
  public String getSdkHomePath() {
    return mySdkHomePath;
  }

  public void setSdkHomePath(String sdkHomePath) {
    mySdkHomePath = sdkHomePath;
  }
}
