package org.jetbrains.plugins.gradle.importing.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;

import javax.swing.*;

/**
 * Is assumed to address the following concerns:
 * <pre>
 * <ul>
 *   <li>allow to choose target project to import (multiple projects may present if we performed recursive file system search);</li>
 *   <li>allow to customise target project settings (project files location, source level etc);</li>
 * </ul>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 12:31 PM
 */
public class GradleDefineProjectToImportStep extends ProjectImportWizardStep {

  private final JPanel myComponent = new JPanel();

  public GradleDefineProjectToImportStep(WizardContext context) {
    super(context);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateDataModel() {
    // TODO den implement
  }
}
