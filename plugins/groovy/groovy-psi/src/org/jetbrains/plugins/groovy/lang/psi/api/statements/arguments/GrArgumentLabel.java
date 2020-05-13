// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyWriteReference;

/**
 * @author ilyas
 */
public interface GrArgumentLabel extends GroovyPsiElement, GroovyReference {

  GrArgumentLabel[] EMPTY_ARRAY = new GrArgumentLabel[0];

  @NotNull
  PsiElement getNameElement();

  /**
   * @return expression which is put into parentheses.
   */
  @Nullable
  GrExpression getExpression();

  @Nullable
  String getName();

  PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException;

  @Nullable
  PsiType getExpectedArgumentType();

  GrNamedArgument getNamedArgument();

  /**
   * @return not-null value if this label is a reference to property in map-constructor invocation,
   * e.g. {@code Person p = [<ref>name</ref>: "John"]} or {@code def p = [<ref>name</ref>: "John"] as Person}
   */
  @Nullable
  GroovyPropertyWriteReference getConstructorPropertyReference();
}
