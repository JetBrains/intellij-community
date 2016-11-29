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
package org.jetbrains.java.decompiler.code.cfg;

import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock implements IGraphNode {

  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int id = 0;

  public int mark = 0;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private InstructionSequence seq = new SimpleInstructionSequence();

  private List<BasicBlock> preds = new ArrayList<>();

  private List<BasicBlock> succs = new ArrayList<>();

  private final List<Integer> instrOldOffsets = new ArrayList<>();

  private List<BasicBlock> predExceptions = new ArrayList<>();

  private List<BasicBlock> succExceptions = new ArrayList<>();

  public BasicBlock(int id) {
    this.id = id;
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public Object clone() {
    BasicBlock block = new BasicBlock(id);

    block.setSeq(seq.clone());
    block.instrOldOffsets.addAll(instrOldOffsets);

    return block;
  }

  public void free() {
    preds.clear();
    succs.clear();
    instrOldOffsets.clear();
    succExceptions.clear();
    seq = new SimpleInstructionSequence();
  }

  public Instruction getInstruction(int index) {
    return seq.getInstr(index);
  }

  public Instruction getLastInstruction() {
    if (seq.isEmpty()) {
      return null;
    }
    else {
      return seq.getLastInstr();
    }
  }

  public Integer getOldOffset(int index) {
    if(index < instrOldOffsets.size()) {
      return instrOldOffsets.get(index);
    } else {
      return -1;
    }
  }

  public int size() {
    return seq.length();
  }

  public void addPredecessor(BasicBlock block) {
    preds.add(block);
  }

  public void removePredecessor(BasicBlock block) {
    while (preds.remove(block)) ;
  }

  public void addSuccessor(BasicBlock block) {
    succs.add(block);
    block.addPredecessor(this);
  }

  public void removeSuccessor(BasicBlock block) {
    while (succs.remove(block)) ;
    block.removePredecessor(this);
  }

  // FIXME: unify block comparisons: id or direct equality
  public void replaceSuccessor(BasicBlock oldBlock, BasicBlock newBlock) {
    for (int i = 0; i < succs.size(); i++) {
      if (succs.get(i).id == oldBlock.id) {
        succs.set(i, newBlock);
        oldBlock.removePredecessor(this);
        newBlock.addPredecessor(this);
      }
    }

    for (int i = 0; i < succExceptions.size(); i++) {
      if (succExceptions.get(i).id == oldBlock.id) {
        succExceptions.set(i, newBlock);
        oldBlock.removePredecessorException(this);
        newBlock.addPredecessorException(this);
      }
    }
  }

  public void addPredecessorException(BasicBlock block) {
    predExceptions.add(block);
  }

  public void removePredecessorException(BasicBlock block) {
    while (predExceptions.remove(block)) ;
  }

  public void addSuccessorException(BasicBlock block) {
    if (!succExceptions.contains(block)) {
      succExceptions.add(block);
      block.addPredecessorException(this);
    }
  }

  public void removeSuccessorException(BasicBlock block) {
    while (succExceptions.remove(block)) ;
    block.removePredecessorException(this);
  }

  public String toString() {
    return toString(0);
  }

  public String toString(int indent) {

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    return id + ":" + new_line_separator + seq.toString(indent);
  }

  public String toStringOldIndices() {

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    StringBuilder buf = new StringBuilder();

    for (int i = 0; i < seq.length(); i++) {
      if (i < instrOldOffsets.size()) {
        buf.append(instrOldOffsets.get(i));
      }
      else {
        buf.append("-1");
      }
      buf.append(": ");
      buf.append(seq.getInstr(i).toString());
      buf.append(new_line_separator);
    }

    return buf.toString();
  }

  public boolean isSuccessor(BasicBlock block) {
    for (BasicBlock succ : succs) {
      if (succ.id == block.id) {
        return true;
      }
    }
    return false;
  }

  public boolean isPredecessor(BasicBlock block) {
    for (int i = 0; i < preds.size(); i++) {
      if (preds.get(i).id == block.id) {
        return true;
      }
    }
    return false;
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public List<Integer> getInstrOldOffsets() {
    return instrOldOffsets;
  }

  public List<? extends IGraphNode> getPredecessors() {
    List<BasicBlock> lst = new ArrayList<>(preds);
    lst.addAll(predExceptions);
    return lst;
  }

  public List<BasicBlock> getPreds() {
    return preds;
  }

  public void setPreds(List<BasicBlock> preds) {
    this.preds = preds;
  }

  public InstructionSequence getSeq() {
    return seq;
  }

  public void setSeq(InstructionSequence seq) {
    this.seq = seq;
  }

  public List<BasicBlock> getSuccs() {
    return succs;
  }

  public void setSuccs(List<BasicBlock> succs) {
    this.succs = succs;
  }


  public List<BasicBlock> getSuccExceptions() {
    return succExceptions;
  }


  public void setSuccExceptions(List<BasicBlock> succExceptions) {
    this.succExceptions = succExceptions;
  }

  public List<BasicBlock> getPredExceptions() {
    return predExceptions;
  }

  public void setPredExceptions(List<BasicBlock> predExceptions) {
    this.predExceptions = predExceptions;
  }
}
