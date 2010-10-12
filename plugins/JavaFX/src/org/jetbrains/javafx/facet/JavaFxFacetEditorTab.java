package org.jetbrains.javafx.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nls;
import org.jetbrains.javafx.JavaFxConfigureSdkPanel;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
class JavaFxFacetEditorTab extends FacetEditorTab {
  private JPanel myContentPane;
  private JavaFxConfigureSdkPanel myJavaFxConfigureSdkPanel;

  private final JavaFxFacetConfiguration myFacetConfiguration;

  public JavaFxFacetEditorTab(final JavaFxFacetConfiguration facetConfiguration,
                              final FacetEditorContext context,
                              FacetValidatorsManager validatorsManager) {
    myFacetConfiguration = facetConfiguration;
    myJavaFxConfigureSdkPanel.registerValidator(validatorsManager);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "JavaFX";
  }

  @Override
  public JComponent createComponent() {
    return myContentPane;
  }

  @Override
  public boolean isModified() {
    final Sdk selectedSdk = myJavaFxConfigureSdkPanel.getSelectedSdk();
    final Sdk fxSdk = myFacetConfiguration.getJavaFxSdk();
    if (selectedSdk != null) {
      if (fxSdk == null || !selectedSdk.getName().equals(fxSdk.getName())) {
        return true;
      }
    }
    else if (fxSdk != null && !fxSdk.equals(selectedSdk)) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() {
    final Sdk sdk = myJavaFxConfigureSdkPanel.getSelectedSdk();
    myFacetConfiguration.setJavaFxSdk(sdk);
  }

  @Override
  public void reset() {
    myJavaFxConfigureSdkPanel.resetSdk(myFacetConfiguration.getJavaFxSdk());
  }

  @Override
  public void disposeUIResources() {
  }
}
