/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.java.decompiler.code.interpreter.Util;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.util.TextUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public abstract class InstructionSequence {

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  protected final VBStyleCollection<Instruction, Integer> collinstr;

  protected int pointer = 0;

  protected ExceptionTable exceptionTable = ExceptionTable.EMPTY;

  protected InstructionSequence() {
    this(new VBStyleCollection<>());
  }

  protected InstructionSequence(VBStyleCollection<Instruction, Integer> collinstr) {
    this.collinstr = collinstr;
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  // to nbe overwritten
  public InstructionSequence clone() {
    return null;
  }

  public void clear() {
    collinstr.clear();
    pointer = 0;
    exceptionTable = ExceptionTable.EMPTY;
  }

  public void addInstruction(Instruction inst, int offset) {
    collinstr.addWithKey(inst, offset);
  }

  public void addInstruction(int index, Instruction inst, int offset) {
    collinstr.addWithKeyAndIndex(index, inst, offset);
  }

  public void addSequence(InstructionSequence seq) {
    for (int i = 0; i < seq.length(); i++) {
      addInstruction(seq.getInstr(i), -1); // TODO: any sensible value possible?
    }
  }

  public void removeInstruction(int index) {
    collinstr.remove(index);
  }

  public void removeLast() {
    if (!collinstr.isEmpty()) {
      collinstr.remove(collinstr.size() - 1);
    }
  }

  public Instruction getCurrentInstr() {
    return collinstr.get(pointer);
  }

  public Instruction getInstr(int index) {
  return collinstr.get(index);
  }

  public Instruction getLastInstr() {
  return collinstr.getLast();
  }

  public int getCurrentOffset() {
    return collinstr.getKey(pointer).intValue();
  }

public int getOffset(int index) {
    return collinstr.getKey(index).intValue();
  }

  public int getPointerByAbsOffset(int offset) {
    Integer absoffset = new Integer(offset);
    if (collinstr.containsKey(absoffset)) {
      return collinstr.getIndexByKey(absoffset);
    }
    else {
      return -1;
    }
  }

  public int getPointerByRelOffset(int offset) {
    Integer absoffset = new Integer(collinstr.getKey(pointer).intValue() + offset);
    if (collinstr.containsKey(absoffset)) {
      return collinstr.getIndexByKey(absoffset);
    }
    else {
      return -1;
    }
  }

  public void setPointerByAbsOffset(int offset) {
    Integer absoffset = new Integer(collinstr.getKey(pointer).intValue() + offset);
    if (collinstr.containsKey(absoffset)) {
      pointer = collinstr.getIndexByKey(absoffset);
    }
  }

  public int length() {
    return collinstr.size();
  }

  public boolean isEmpty() {
    return collinstr.isEmpty();
  }

  public void addToPointer(int diff) {
    this.pointer += diff;
  }

  public String toString() {
    return toString(0);
  }

  public String toString(int indent) {

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    StringBuilder buf = new StringBuilder();

    for (int i = 0; i < collinstr.size(); i++) {
    buf.append(TextUtil.getIndentString(indent));
      buf.append(collinstr.getKey(i).intValue());
      buf.append(": ");
      buf.append(collinstr.get(i).toString());
      buf.append(new_line_separator);
    }

    return buf.toString();
  }

  public void writeCodeToStream(DataOutputStream out) throws IOException {

    for (int i = 0; i < collinstr.size(); i++) {
      collinstr.get(i).writeToStream(out, collinstr.getKey(i).intValue());
    }
  }

  public void writeExceptionsToStream(DataOutputStream out) throws IOException {

    List<ExceptionHandler> handlers = exceptionTable.getHandlers();

    out.writeShort(handlers.size());
    for (int i = 0; i < handlers.size(); i++) {
      handlers.get(i).writeToStream(out);
    }
  }

  public void sortHandlers(final StructContext context) {

    Collections.sort(exceptionTable.getHandlers(), (handler0, handler1) -> {

      if (handler0.to == handler1.to) {
        if (handler0.exceptionClass == null) {
          return 1;
        }
        else {
          if (handler1.exceptionClass == null) {
            return -1;
          }
          else if (handler0.exceptionClass.equals(handler1.exceptionClass)) {
            return (handler0.from > handler1.from) ? -1 : 1; // invalid code
          }
          else {
            if (Util.instanceOf(context, handler0.exceptionClass, handler1.exceptionClass)) {
              return -1;
            }
            else {
              return 1;
            }
          }
        }
      }
      else {
        return (handler0.to > handler1.to) ? 1 : -1;
      }
    });
  }


  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public int getPointer() {
    return pointer;
  }

  public void setPointer(int pointer) {
    this.pointer = pointer;
  }

  public ExceptionTable getExceptionTable() {
    return exceptionTable;
  }

  public void setExceptionTable(ExceptionTable exceptionTable) {
    this.exceptionTable = exceptionTable;
  }
}
