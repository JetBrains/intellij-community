// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

final class CompositeOperatorTransformation extends SimpleBinaryTransformation {
  private final @NotNull String myOperatorText;

  public CompositeOperatorTransformation(@NotNull IElementType operatorType, @NotNull String operatorText) {
    super(operatorType);
    myOperatorText = operatorText;
  }

  @Override
  protected @NotNull String getOperatorText() {
    return myOperatorText;
  }
}
