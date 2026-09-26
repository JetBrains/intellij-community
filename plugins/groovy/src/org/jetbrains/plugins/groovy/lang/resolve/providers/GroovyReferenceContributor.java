// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.providers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.ext.spock.SpockUnrollReferenceProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

/**
 * @author Dmitry.Krasilschikov
 */
public final class GroovyReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrLiteral.class), new PropertiesReferenceProvider(),
                                        PsiReferenceRegistrar.LOWER_PRIORITY);

    registrar.registerReferenceProvider(GroovyPatterns.stringLiteral().withParent(GrAnnotationNameValuePair.class),
                                        new SpockUnrollReferenceProvider());
  }
}
