package org.jetbrains.android.run;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInheritingClassVisibilityChecker extends AndroidClassVisibilityCheckerBase {
  private final Project myProject;
  private final String myBaseClassName;

  public AndroidInheritingClassVisibilityChecker(@NotNull Project project,
                                                 @NotNull ConfigurationModuleSelector moduleSelector,
                                                 @NotNull String baseClassName) {
    super(moduleSelector);
    myProject = project;
    myBaseClassName = baseClassName;
  }


  @Override
  protected boolean isVisible(@NotNull Module module, @NotNull PsiClass aClass) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiClass baseClass = facade.findClass(myBaseClassName, module.getModuleWithDependenciesAndLibrariesScope(true));
    return baseClass != null && (aClass).isInheritor(baseClass, true);
  }
}
