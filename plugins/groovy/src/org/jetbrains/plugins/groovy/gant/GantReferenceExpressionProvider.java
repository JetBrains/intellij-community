package org.jetbrains.plugins.groovy.gant;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class GantReferenceExpressionProvider extends PsiReferenceProvider {

  public static final Class SCOPE_CLASS = GrReferenceExpression.class;

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof GrReferenceExpression) {
      PsiFile file = element.getContainingFile();
      if (GantUtils.isGantScriptFile(file)) {
        return new PsiReference[]{new GantTargetReference(((GrReferenceExpression)element))};
      }

    }
    return PsiReference.EMPTY_ARRAY;
  }
}
