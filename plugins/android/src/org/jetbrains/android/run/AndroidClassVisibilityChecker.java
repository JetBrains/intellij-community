package org.jetbrains.android.run;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassVisibilityChecker implements JavaCodeFragment.VisibilityChecker {
  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private final String myBaseClassName;

  public AndroidClassVisibilityChecker(@NotNull Project project,
                                       @NotNull ConfigurationModuleSelector moduleSelector,
                                       @NotNull String baseClassName) {
    myProject = project;
    myModuleSelector = moduleSelector;
    myBaseClassName = baseClassName;
  }


  @Override
  public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
    if (!(declaration instanceof PsiClass)) {
      return Visibility.NOT_VISIBLE;
    }

    final Module module = myModuleSelector.getModule();
    if (module == null) {
      return Visibility.NOT_VISIBLE;
    }

    final PsiFile file = declaration.getContainingFile();
    final VirtualFile vFile = file != null ? file.getVirtualFile() : null;
    if (vFile == null) {
      return Visibility.NOT_VISIBLE;
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiClass baseClass = facade.findClass(myBaseClassName, module.getModuleWithDependenciesAndLibrariesScope(true));
    if (baseClass == null ||
        !((PsiClass)declaration).isInheritor(baseClass, true)) {
      return Visibility.NOT_VISIBLE;
    }
    return Visibility.VISIBLE;
  }
}
