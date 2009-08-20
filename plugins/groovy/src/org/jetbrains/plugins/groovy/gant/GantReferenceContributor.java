package org.jetbrains.plugins.groovy.gant;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.patterns.PlatformPatterns;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class GantReferenceContributor extends PsiReferenceContributor {

  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrReferenceExpression.class),
                                        new GantReferenceExpressionProvider());
  }

}
