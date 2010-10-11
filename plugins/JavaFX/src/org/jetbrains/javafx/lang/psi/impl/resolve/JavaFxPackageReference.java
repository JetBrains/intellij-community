package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPackageReference extends JavaFxReference {
  public JavaFxPackageReference(final JavaFxReferenceElement psiElement) {
    super(psiElement);
  }

  @Nullable
  protected PsiPackage findPackage(final String qualifiedName) {
    return JavaPsiFacade.getInstance(myElement.getProject()).findPackage(qualifiedName);
  }

  @Override
  protected ResolveResult[] multiResolveInner(final boolean incompleteCode) {
    return JavaFxResolveUtil.createResolveResult(findPackage(myElement.getQualifiedName().toString()));
  }
}
