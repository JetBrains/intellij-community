package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Handles the following responsibilities:
 * <pre>
 * <ul>
 *   <li>allows end user to define gradle project file to import from;</li>
 *   <li>processes the input and reacts accordingly - shows error message if the project is invalid or proceeds to the next screen;</li>
 * </ul>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 4:15 PM
 */
public class GradleSelectProjectStep extends GradleImportWizardStep {

  private final JPanel myComponent = new JPanel(new GridBagLayout());
  private final NamePathComponent myRootPathComponent;
  
  public GradleSelectProjectStep(@NotNull WizardContext context) {
    super(context);
    
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    
    myRootPathComponent = new NamePathComponent(
      "", GradleBundle.message("gradle.import.label.select.project"), GradleBundle.message("gradle.import.title.select.project"), "", 
      false, 
      false
    );
    myRootPathComponent.setNameComponentVisible(false);
    myComponent.add(myRootPathComponent, constraints);
    myComponent.add(Box.createVerticalGlue());
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateStep() {
    if (!myRootPathComponent.isPathChangedByUser()) {
      myRootPathComponent.setPath(getBuilder().getRootDirectoryPath(getWizardContext()));
    }
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public boolean validate() throws ConfigurationException {
    storeCurrentSettings();
    getBuilder().ensureProjectIsDefined();
    return true;
  }

  @Override
  public void onStepLeaving() {
    storeCurrentSettings();
  }

  private void storeCurrentSettings() {
    if (myRootPathComponent.isPathChangedByUser()) {
      getBuilder().setCurrentProjectPath(myRootPathComponent.getPath());
    }
  }
}
