// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class CompositeOperatorTransformation extends SimpleBinaryTransformation {

  private final @NotNull String myOperatorText;

  public CompositeOperatorTransformation(@NotNull IElementType operatorType, @NotNull String operatorText) {
    super(operatorType);
    myOperatorText = operatorText;
  }

  @NotNull
  @Override
  protected String getOperatorText() {
    return myOperatorText;
  }
}
