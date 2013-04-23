package org.jetbrains.android.run;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidClassVisibilityCheckerBase implements JavaCodeFragment.VisibilityChecker {
  private final ConfigurationModuleSelector myModuleSelector;

  public AndroidClassVisibilityCheckerBase(@NotNull ConfigurationModuleSelector moduleSelector) {
    myModuleSelector = moduleSelector;
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
    return isVisible(module, (PsiClass)declaration) ? Visibility.VISIBLE : Visibility.NOT_VISIBLE;
  }

  protected abstract boolean isVisible(@NotNull Module module, @NotNull PsiClass aClass);
}
