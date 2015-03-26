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
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;

public class BasicBlockStatement extends Statement {

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private final BasicBlock block;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public BasicBlockStatement(BasicBlock block) {

    type = Statement.TYPE_BASICBLOCK;

    this.block = block;

    id = block.id;
    CounterContainer coun = DecompilerContext.getCounterContainer();
    if (id >= coun.getCounter(CounterContainer.STATEMENT_COUNTER)) {
      coun.setCounter(CounterContainer.STATEMENT_COUNTER, id + 1);
    }

    Instruction instr = block.getLastInstruction();
    if (instr != null) {
      if (instr.group == CodeConstants.GROUP_JUMP && instr.opcode != CodeConstants.opc_goto) {
        lastBasicType = LASTBASICTYPE_IF;
      }
      else if (instr.group == CodeConstants.GROUP_SWITCH) {
        lastBasicType = LASTBASICTYPE_SWITCH;
      }
    }

    // monitorenter and monitorexits
    buildMonitorFlags();
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    return ExprProcessor.listToJava(varDefinitions, indent, tracer).append(ExprProcessor.listToJava(exprents, indent, tracer));
  }

  public Statement getSimpleCopy() {

    BasicBlock newblock = new BasicBlock(
      DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER));

    SimpleInstructionSequence seq = new SimpleInstructionSequence();
    for (int i = 0; i < block.getSeq().length(); i++) {
      seq.addInstruction(block.getSeq().getInstr(i).clone(), -1);
    }

    newblock.setSeq(seq);

    return new BasicBlockStatement(newblock);
  }


  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public BasicBlock getBlock() {
    return block;
  }
}
