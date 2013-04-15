package org.jetbrains.android.run.testing;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.run.AndroidClassVisibilityCheckerBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestClassVisibilityChecker extends AndroidClassVisibilityCheckerBase {
  public AndroidTestClassVisibilityChecker(@NotNull ConfigurationModuleSelector moduleSelector) {
    super(moduleSelector);
  }

  @Override
  protected boolean isVisible(@NotNull Module module, @NotNull PsiClass aClass) {
    return JUnitUtil.isTestClass(aClass);
  }
}
