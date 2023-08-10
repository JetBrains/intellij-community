// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrRangeExpression extends GrExpression {

  enum BoundaryType {
    CLOSED, LEFT_OPEN, RIGHT_OPEN, BOTH_OPEN
  }

  @NotNull
  GrExpression getFrom();

  @Nullable
  GrExpression getTo();

  @Nullable
  BoundaryType getBoundaryType();
}
