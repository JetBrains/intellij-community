package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.javafx.lang.psi.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxThisReference extends JavaFxReference {
  public JavaFxThisReference(final JavaFxReferenceElement psiElement) {
    super(psiElement);
  }

  @Override
  protected ResolveResult[] multiResolveInner(boolean incompleteCode) {
    PsiElement element = myElement;
    final JavaFxExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      final PsiReference reference = qualifier.getReference();
      if (reference != null) {
        final PsiElement resolveResult = reference.resolve();
        if (resolveResult != null) {
          element = resolveResult;
        }
      }
    }

    final JavaFxElement parent = PsiTreeUtil.getNonStrictParentOfType(element, JavaFxClassDefinition.class, JavaFxObjectLiteral.class);
    if (parent != null) {
      return JavaFxResolveUtil.createResolveResult(parent);
    }
    return ResolveResult.EMPTY_ARRAY;
  }
}
