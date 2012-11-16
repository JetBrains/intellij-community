package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.wizard.adjust.GradleAdjustImportSettingsStep;
import org.jetbrains.plugins.gradle.importing.wizard.select.GradleSelectProjectStep;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Denis Zhdanov
 * @since 7/29/11 3:45 PM
 */
public class GradleProjectImportProvider extends ProjectImportProvider {

  public GradleProjectImportProvider(GradleProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return new ModuleWizardStep[] { new GradleSelectProjectStep(context), new GradleAdjustImportSettingsStep(context) };
  }


  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return GradleConstants.EXTENSION.equals(file.getExtension());
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "*.gradle";
  }
}
