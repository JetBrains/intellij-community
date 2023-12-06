// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationSupport;

/**
 * Allows to determine whether some part of Abstract Syntax Tree should not be highlighted.
 * This is useful for processing custom AST transformations, where default highlighting may be harmful.
 * See {@link GroovyInlineASTTransformationSupport}
 */
public abstract class GroovyUnresolvedHighlightFilter {

  public static final ExtensionPointName<GroovyUnresolvedHighlightFilter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.unresolvedHighlightFilter");

  public abstract boolean isReject(@NotNull GrReferenceExpression expression);

  public static boolean shouldHighlight(@NotNull GrReferenceExpression expression) {
    for (GroovyUnresolvedHighlightFilter filter : EP_NAME.getExtensions()) {
      if (filter.isReject(expression)) return false;
    }

    return true;
  }
}
