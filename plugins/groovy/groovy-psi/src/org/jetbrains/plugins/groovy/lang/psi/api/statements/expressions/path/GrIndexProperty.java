// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

/**
 * Index access expression: {@code foo[bar]} or {@code foo[bar] = baz}.
 */
public interface GrIndexProperty extends GrExpression {

  /**
   * @return expression on which index access is performed,
   * e.g. {@code foo} in {@code foo[bar]}
   */
  @NotNull
  GrExpression getInvokedExpression();

  /**
   * @return safe access token element, e.g. {@code ?} in {@code foo?[bar]}, or {@code null} if this expression is not safe
   */
  @Nullable
  PsiElement getSafeAccessToken();

  /**
   * @return argument list element, e.g. {@code [bar]} in {@code foo[bar]}
   */
  @NotNull
  GrArgumentList getArgumentList();

  /**
   * @return reference to a {@code getAt} method if this expression is an r-value, e.g. {@code foo[bar]},
   * or {@code null} if this expression is an l-value only, e.g. {@code foo[bar] = baz}
   * or if this expression cannot reference {@code getAt} method. <br/>
   * This method may return non-null reference even if the expression is an l-value too,
   * e.g. {@code foo[bar] += baz} has both r-value and l-value references.
   * @see #getLValueReference()
   */
  @Nullable
  GroovyMethodCallReference getRValueReference();

  /**
   * @return reference to a {@code putAt} method if this expression is an l-value, e.g. {@code foo[bar] = baz},
   * or {@code null} if this expression is an r-value only, e.g. {@code foo[bar]},
   * or if this expression cannot reference {@code putAt} method. <br/>
   * This method may return non-null reference even if the expression is an r-value too,
   * e.g. {@code foo[bar] += baz} has both r-value and l-value references.
   * @see #getRValueReference()
   */
  @Nullable
  GroovyMethodCallReference getLValueReference();
}
