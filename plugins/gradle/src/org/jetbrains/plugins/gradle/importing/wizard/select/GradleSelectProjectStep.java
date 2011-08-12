package org.jetbrains.plugins.gradle.importing.wizard.select;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.wizard.AbstractImportFromGradleWizardStep;
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
public class GradleSelectProjectStep extends AbstractImportFromGradleWizardStep {

  private final JPanel myComponent = new JPanel(new GridBagLayout());
  private final TextFieldWithBrowseButton myProjectPathComponent;
  
  public GradleSelectProjectStep(@NotNull WizardContext context) {
    super(context);
    
    GridBagConstraints constraints = new GridBagConstraints();
    JLabel label = new JLabel(GradleBundle.message("gradle.import.label.select.project"));
    myComponent.add(label);

    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    
    myProjectPathComponent = new TextFieldWithBrowseButton();
    myProjectPathComponent.addBrowseFolderListener(
      "",
      GradleBundle.message("gradle.import.title.select.project"),
      null,
      new FileTypeDescriptor(GradleBundle.message("gradle.import.label.select.project"), "gradle")
    );
    myComponent.add(myProjectPathComponent, constraints);
    myComponent.add(Box.createVerticalGlue());
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateStep() {
    if (isPathChanged()) {
      myProjectPathComponent.setText(getBuilder().getProjectPath(getContext()));
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
    if (isPathChanged()) {
      getBuilder().setCurrentProjectPath(myProjectPathComponent.getText());
    }
  }

  private boolean isPathChanged() {
    return !StringUtil.equals(myProjectPathComponent.getText(), getBuilder().getProjectPath(getContext()));
  }
}
