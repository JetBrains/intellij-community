package org.jetbrains.javafx.lang.psi;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxPsiManager extends ProjectComponent {
  @Nullable
  PsiElement getElementByQualifiedName(final String qualifiedName);

  boolean processPackageFiles(final PsiPackage psiPackage,
                              final PsiScopeProcessor processor,
                              final ResolveState state,
                              final PsiElement lastParent,
                              final PsiElement place);
}
