package com.intellij.php.dev.psiViewer;

import com.intellij.dev.psiViewer.PsiViewerExtension;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Used in PSI viewer tool
 */
public final class PhpJSPsiViewerExtension implements PsiViewerExtension {
  private static final @NlsSafe String PHP_JS = "PHP/JS";

  @Override
  public @NotNull String getName() {
    return PHP_JS;
  }

  @Override
  public @NotNull Icon getIcon() {
    return PhpIcons.PHP_FILE;
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull Project project, @NotNull String text) {
    return PsiFileFactory.getInstance(project).createFileFromText("Dummy.js." + PhpFileType.INSTANCE.getDefaultExtension(),
                                                                  PhpFileType.INSTANCE,
                                                                  text,
                                                                  System.currentTimeMillis(),
                                                                  false);
  }

  @Override
  public @NotNull FileType getDefaultFileType() {
    return PhpFileType.INSTANCE;
  }
}
