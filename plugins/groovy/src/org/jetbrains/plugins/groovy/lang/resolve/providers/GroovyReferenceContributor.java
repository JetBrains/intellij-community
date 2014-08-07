/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve.providers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;
import org.jetbrains.plugins.groovy.spock.SpockUnrollReferenceProvider;

/**
 * @author Dmitry.Krasilschikov
 */
public class GroovyReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrLiteral.class), new PropertiesReferenceProvider());

    registrar.registerReferenceProvider(GroovyPatterns.stringLiteral().withParent(GrAnnotationNameValuePair.class),
                                        new SpockUnrollReferenceProvider());

  }
}
