/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * @author Vladislav.Soroka
 * @since 11/1/13
 */
public abstract class AbstractGradleCompletionContributor extends CompletionContributor {
  protected static final ElementPattern<PsiElement> GRADLE_FILE_PATTERN = psiElement()
    .inFile(psiFile().withName(string().endsWith('.' + GradleConstants.EXTENSION)));

  @Nullable
  protected String findNamedArgumentValue(@Nullable GrNamedArgumentsOwner namedArgumentsOwner, @NotNull String label) {
    if (namedArgumentsOwner == null) return null;
    GrNamedArgument namedArgument = namedArgumentsOwner.findNamedArgument(label);
    if (namedArgument == null) return null;

    GrExpression expression = namedArgument.getExpression();
    if (!(expression instanceof GrLiteralImpl)) return null;
    Object value = GrLiteralImpl.class.cast(expression).getValue();
    return value == null ? null : String.valueOf(value);
  }
}
