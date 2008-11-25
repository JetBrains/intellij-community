package org.jetbrains.plugins.groovy.lang.resolve.providers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Dmitry.Krasilschikov
 */
public class PropertiesReferenceContributer extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrLiteral.class),
                                        new PropertiesReferenceProvider());
  }
}
