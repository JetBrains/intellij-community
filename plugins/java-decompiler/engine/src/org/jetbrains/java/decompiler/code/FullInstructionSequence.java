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
package org.jetbrains.java.decompiler.code;

import org.jetbrains.java.decompiler.util.VBStyleCollection;


public class FullInstructionSequence extends InstructionSequence {

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public FullInstructionSequence(VBStyleCollection<Instruction, Integer> collinstr, ExceptionTable extable) {
    super(collinstr);
    this.exceptionTable = extable;

    // translate raw exception handlers to instr
    for (ExceptionHandler handler : extable.getHandlers()) {
      handler.from_instr = this.getPointerByAbsOffset(handler.from);
      handler.to_instr = this.getPointerByAbsOffset(handler.to);
      handler.handler_instr = this.getPointerByAbsOffset(handler.handler);
    }
  }
}
