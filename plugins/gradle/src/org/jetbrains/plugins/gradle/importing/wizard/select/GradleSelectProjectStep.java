package org.jetbrains.plugins.gradle.importing.wizard.select;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleConfigurable;
import org.jetbrains.plugins.gradle.config.GradleHomeSettingType;
import org.jetbrains.plugins.gradle.importing.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.importing.wizard.AbstractImportFromGradleWizardStep;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

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

  private final JPanel             myComponent          = new JPanel(new GridBagLayout());
  private final GridBagConstraints myLabelConstraints   = new GridBagConstraints();
  private final GridBagConstraints myControlConstraints = new GridBagConstraints();

  private final TextFieldWithBrowseButton myProjectPathComponent;

  private GradleConfigurable myConfigurable;
  private boolean            myGradleSettingsInitialised;

  public GradleSelectProjectStep(@NotNull WizardContext context) {
    super(context);

    myLabelConstraints.anchor = GridBagConstraints.WEST;
    JLabel label = new JLabel(GradleBundle.message("gradle.import.label.select.project"));
    myComponent.add(label, myLabelConstraints);

    myControlConstraints.gridwidth = GridBagConstraints.REMAINDER;
    myControlConstraints.weightx = 1;
    myControlConstraints.fill = GridBagConstraints.HORIZONTAL;

    myProjectPathComponent = new TextFieldWithBrowseButton();
    myProjectPathComponent.addBrowseFolderListener(
      "",
      GradleBundle.message("gradle.import.title.select.project"),
      null,
      GradleUtil.getFileChooserDescriptor()
    );
    myProjectPathComponent.setText(context.getProjectFileDirectory());
    myComponent.add(myProjectPathComponent, myControlConstraints);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateStep() {
    if (!myGradleSettingsInitialised) {
      initGradleSettingsControl();
    }
    if (myConfigurable != null) {
      myConfigurable.reset();
    }
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public String getHelpId() {
    return GradleConstants.HELP_TOPIC_IMPORT_SELECT_PROJECT_STEP;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    final GradleHomeSettingType settingType = myConfigurable.getCurrentGradleHomeSettingType();
    if (settingType == GradleHomeSettingType.EXPLICIT_INCORRECT) {
      GradleUtil.showBalloon(
        myConfigurable.getGradleHomeComponent().getPathComponent(),
        MessageType.ERROR,
        GradleBundle.message("gradle.home.setting.type.explicit.incorrect")
      );
      return false;
    }
    if (settingType == GradleHomeSettingType.UNKNOWN) {
      GradleUtil.showBalloon(
        myConfigurable.getGradleHomeComponent().getPathComponent(),
        MessageType.ERROR,
        GradleBundle.message("gradle.home.setting.type.unknown")
      );
      return false;
    }
    storeCurrentSettings();
    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return false;
    }
    builder.ensureProjectIsDefined(getWizardContext());
    return true;
  }

  @Override
  public void onStepLeaving() {
    storeCurrentSettings();
  }

  private void storeCurrentSettings() {
    GradleProjectImportBuilder builder = getBuilder();
    if (builder != null && isPathChanged()) {
      builder.setCurrentProjectPath(myProjectPathComponent.getText());
    }
    if (myConfigurable != null && myConfigurable.isModified()) {
      myConfigurable.apply();
    }
    if (builder != null) {
      final String path = builder.getProjectPath(getWizardContext());
      final File parent = new File(path).getParentFile();
      if (parent != null) {
        getWizardContext().setProjectName(parent.getName());
      }
    }
  }

  private boolean isPathChanged() {
    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return false;
    }
    return !StringUtil.equals(myProjectPathComponent.getText(), builder.getProjectPath(getWizardContext()));
  }

  private void initGradleSettingsControl() {
    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return;
    }
    Project project = builder.getProject(getWizardContext());
    myConfigurable = new GradleConfigurable(project);
    NamePathComponent gradleHomeComponent = myConfigurable.getGradleHomeComponent();
    myLabelConstraints.insets.top = myControlConstraints.insets.top = 15;
    myComponent.add(gradleHomeComponent.getPathLabel(), myLabelConstraints);
    myComponent.add(gradleHomeComponent.getPathPanel(), myControlConstraints);
    myGradleSettingsInitialised = true;
  }
}
