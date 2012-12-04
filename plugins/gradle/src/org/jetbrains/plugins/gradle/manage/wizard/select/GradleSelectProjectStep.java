package org.jetbrains.plugins.gradle.manage.wizard.select;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleConfigurable;
import org.jetbrains.plugins.gradle.config.GradleHomeSettingType;
import org.jetbrains.plugins.gradle.manage.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.manage.wizard.AbstractImportFromGradleWizardStep;
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

  private final JPanel myComponent = new JPanel(new BorderLayout());
  
  @NotNull private GradleConfigurable myConfigurable;
  
  private boolean myGradleSettingsInitialised;

  public GradleSelectProjectStep(@NotNull WizardContext context) {
    super(context);
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
        myConfigurable.getGradleHomePathField(),
        MessageType.ERROR,
        GradleBundle.message("gradle.home.setting.type.explicit.incorrect")
      );
      return false;
    }
    if (settingType == GradleHomeSettingType.UNKNOWN) {
      GradleUtil.showBalloon(
        myConfigurable.getGradleHomePathField(),
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
    if (myConfigurable.isModified()) {
      myConfigurable.apply();
    }
    final String projectPath = myConfigurable.getLinkedProjectPath();
    if (projectPath != null) {
      final File parent = new File(projectPath).getParentFile();
      if (parent != null) {
        getWizardContext().setProjectName(parent.getName());
      }
    }
  }

  private void initGradleSettingsControl() {
    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return;
    }
    builder.prepare(getWizardContext());
    myConfigurable = builder.getConfigurable();
    myComponent.add(myConfigurable.createComponent());
    myGradleSettingsInitialised = true;
  }
}
