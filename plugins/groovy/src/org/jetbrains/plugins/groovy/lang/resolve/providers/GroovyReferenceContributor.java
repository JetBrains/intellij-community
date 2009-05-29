package org.jetbrains.plugins.groovy.lang.resolve.providers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.references.GroovyDocReferenceProvider;

/**
 * @author Dmitry.Krasilschikov
 */
public class GroovyReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrLiteral.class), new PropertiesReferenceProvider());

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GroovyDocPsiElement.class), new GroovyDocReferenceProvider());
  }
}
