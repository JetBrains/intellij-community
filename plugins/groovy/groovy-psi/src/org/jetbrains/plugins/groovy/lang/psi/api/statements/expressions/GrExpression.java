// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

public interface GrExpression extends GrStatement, GrAnnotationMemberValue {
  GrExpression[] EMPTY_ARRAY = new GrExpression[0];

  default @Nullable PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GrTypeCalculator::getTypeFromCalculators);
  }

  @Nullable
  PsiType getNominalType();

  GrExpression replaceWithExpression(@NotNull GrExpression expression, boolean removeUnnecessaryParentheses);
}
