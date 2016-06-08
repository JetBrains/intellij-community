package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.codeInsight.daemon.impl.JavaProjectSdkSetupValidator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxProjectSdkSetupValidator implements ProjectSdkSetupValidator {

  @Override
  public boolean isApplicableFor(@NotNull Project project, @NotNull VirtualFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  @Nullable
  @Override
  public String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
    final String javaErrorMessage = JavaProjectSdkSetupValidator.INSTANCE.getErrorMessage(project, file);
    if (javaErrorMessage != null) {
      return javaErrorMessage;
    }
    final PsiClass nodeClass =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_SCENE_NODE, GlobalSearchScope.allScope(project));
    if (nodeClass == null) {
      return "The JavaFX runtime is not configured. " +
             "Either use a JDK that has the JavaFX built in, or add a JavaFX library to the classpath";
    }
    return null;
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull VirtualFile file) {
    final String javaErrorMessage = JavaProjectSdkSetupValidator.INSTANCE.getErrorMessage(project, file);
    if (javaErrorMessage != null) {
      JavaProjectSdkSetupValidator.INSTANCE.doFix(project, file);
      return;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    final String moduleName = module != null && !module.isDisposed() ? module.getName() : null;
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(moduleName, ClasspathEditor.NAME);
  }
}
