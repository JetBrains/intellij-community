package com.intellij.appengine.facet;

import com.intellij.facet.ui.*;
import com.intellij.facet.Facet;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.appengine.sdk.impl.AppEngineSdkImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineFacetEditor extends FacetEditorTab {
  private final AppEngineFacetConfiguration myFacetConfiguration;
  private final FacetEditorContext myContext;
  private JPanel myMainPanel;
  private JPanel mySdkEditorPanel;
  private AppEngineSdkEditor mySdkEditor;

  public AppEngineFacetEditor(AppEngineFacetConfiguration facetConfiguration, FacetEditorContext context, FacetValidatorsManager validatorsManager) {
    myFacetConfiguration = facetConfiguration;
    myContext = context;
    mySdkEditor = new AppEngineSdkEditor(myContext.getProject());
    validatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        return AppEngineSdkImpl.checkPath(mySdkEditor.getPath());
      }
    }, mySdkEditor.getComboBox());
  }

  @Nls
  public String getDisplayName() {
    return "Google App Engine";
  }

  public JComponent createComponent() {
    mySdkEditorPanel.add(mySdkEditor.getMainComponent());
    return myMainPanel;
  }

  public boolean isModified() {
    return !mySdkEditor.getPath().equals(myFacetConfiguration.getSdkHomePath());
  }

  public void apply() throws ConfigurationException {
    myFacetConfiguration.setSdkHomePath(mySdkEditor.getPath());
  }

  public void reset() {
    mySdkEditor.setPath(myFacetConfiguration.getSdkHomePath());
    if (myContext.isNewFacet() && myFacetConfiguration.getSdkHomePath().length() == 0) {
      mySdkEditor.setDefaultPath();
    }
  }

  public void disposeUIResources() {
  }

  @Override
  public void onFacetInitialized(@NotNull Facet facet) {
    ((AppEngineFacet)facet).getSdk().getOrCreateAppServer();
  }
}
