// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author Max Medvedev
 */
public class ThrowingInstruction extends InstructionImpl {
  public ThrowingInstruction(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  @NonNls
  public String toString() {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append(num());
    builder.append("(");
    for (Instruction successor : allSuccessors()) {
      builder.append(successor.num());
      builder.append(',');
    }
    if (allPredecessors().iterator().hasNext()) builder.delete(builder.length() - 1, builder.length());
    builder.append(") ").append("THROW. ").append(getElementPresentation());
    return builder.toString();
  }
}
