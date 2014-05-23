/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
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
  public String toString() {
    final StringBuilder builder = new StringBuilder();
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
