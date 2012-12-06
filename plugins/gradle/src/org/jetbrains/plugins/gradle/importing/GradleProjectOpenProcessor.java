package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.wizard.select.GradleSelectProjectStep;

/**
 * @author Denis Zhdanov
 * @since 11/1/11 4:45 PM
 */
public class GradleProjectOpenProcessor extends ProjectOpenProcessorBase<GradleProjectImportBuilder> {
  
  public static final String[] BUILD_FILE_NAMES = { "build.gradle" };
  
  public GradleProjectOpenProcessor(@NotNull GradleProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  public String[] getSupportedExtensions() {
    return BUILD_FILE_NAMES;
  }

  @Override
  protected boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    AddModuleWizard dialog = new AddModuleWizard(null, file.getPath(), new GradleProjectImportProvider(new GradleProjectImportBuilder()));
    getBuilder().setCurrentProjectPath(file.getPath());
    dialog.getWizardContext().setProjectBuilder(getBuilder());
    dialog.navigateToStep(new Function<Step, Boolean>() {
      @Override
      public Boolean fun(Step step) {
        return step instanceof GradleSelectProjectStep;
      }
    });
    dialog.doNextAction();
    if (StringUtil.isEmpty(wizardContext.getProjectName())) {
      final String projectName = dialog.getWizardContext().getProjectName();
      if (!StringUtil.isEmpty(projectName)) {
        wizardContext.setProjectName(projectName);
      }
    }

    dialog.show();
    return dialog.isOK();
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }
}
