/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import org.jetbrains.annotations.NotNull;

public abstract class GrInstruction extends Instruction {

  @Override
  public final DfaInstructionState[] accept(@NotNull DfaMemoryState stateBefore, @NotNull InstructionVisitor visitor) {
    if (visitor instanceof GrInstructionVisitor) {
      return acceptGroovy(stateBefore, (GrInstructionVisitor)visitor);
    }
    else {
      return super.accept(stateBefore, visitor);
    }
  }

  public abstract DfaInstructionState[] acceptGroovy(@NotNull DfaMemoryState state, @NotNull GrInstructionVisitor visitor);
}
