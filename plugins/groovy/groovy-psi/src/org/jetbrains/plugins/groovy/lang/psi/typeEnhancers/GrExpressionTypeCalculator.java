// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @deprecated please use {@link org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator#EP}
 */
@ScheduledForRemoval(inVersion = "2019.2")
@Deprecated
@SuppressWarnings({"DeprecatedIsStillUsed", "ScheduledForRemoval"})
public abstract class GrExpressionTypeCalculator {

  public static final ExtensionPointName<GrExpressionTypeCalculator> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.expressionTypeCalculator");

  @Nullable
  public abstract PsiType calculateType(@NotNull GrExpression expression, @Nullable PsiElement resolved);
}
