/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Instruction {
  Instruction[] EMPTY_ARRAY = new Instruction[0];

  @NotNull
  Iterable<Instruction> successors(@NotNull CallEnvironment environment);

  @NotNull
  Iterable<Instruction> predecessors(@NotNull CallEnvironment environment);

  @NotNull
  Iterable<Instruction> allSuccessors();

  @NotNull
  Iterable<Instruction> allPredecessors();

  int num();

  @NotNull
  Iterable<? extends NegatingGotoInstruction> getNegatingGotoInstruction();

  @Nullable
  PsiElement getElement();
}
