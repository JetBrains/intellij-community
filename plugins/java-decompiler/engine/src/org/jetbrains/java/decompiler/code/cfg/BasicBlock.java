// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.cfg;

import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock implements IGraphNode {
  public final int id;

  private final InstructionSequence seq;
  private final List<Integer> originalOffsets = new ArrayList<>();
  private final List<BasicBlock> predecessors = new ArrayList<>();
  private final List<BasicBlock> successors = new ArrayList<>();
  private final List<BasicBlock> predecessorExceptions = new ArrayList<>();
  private final List<BasicBlock> successorExceptions = new ArrayList<>();

  public int mark = 0;

  public BasicBlock(int id) {
    this(id, new SimpleInstructionSequence());
  }

  public BasicBlock(int id, InstructionSequence seq) {
    this.id = id;
    this.seq = seq;
  }

  public BasicBlock clone(int newId) {
    BasicBlock block = new BasicBlock(newId, seq.clone());
    block.originalOffsets.addAll(originalOffsets);
    return block;
  }

  public Instruction getInstruction(int index) {
    return seq.getInstr(index);
  }

  public Instruction getLastInstruction() {
    return seq.isEmpty() ? null : seq.getLastInstr();
  }

  public Integer getOriginalOffset(int index) {
    return index < originalOffsets.size() ? originalOffsets.get(index) : Integer.valueOf(-1);
  }

  public int size() {
    return seq.length();
  }

  public void addPredecessor(BasicBlock block) {
    predecessors.add(block);
  }

  public void removePredecessor(BasicBlock block) {
    while (predecessors.remove(block)) /**/;
  }

  public void addSuccessor(BasicBlock block) {
    successors.add(block);
    block.addPredecessor(this);
  }

  public void removeSuccessor(BasicBlock block) {
    while (successors.remove(block)) /**/;
    block.removePredecessor(this);
  }

  // FIXME: unify block comparisons: id or direct equality
  public void replaceSuccessor(BasicBlock oldBlock, BasicBlock newBlock) {
    for (int i = 0; i < successors.size(); i++) {
      if (successors.get(i).id == oldBlock.id) {
        successors.set(i, newBlock);
        oldBlock.removePredecessor(this);
        newBlock.addPredecessor(this);
      }
    }

    for (int i = 0; i < successorExceptions.size(); i++) {
      if (successorExceptions.get(i).id == oldBlock.id) {
        successorExceptions.set(i, newBlock);
        oldBlock.removePredecessorException(this);
        newBlock.addPredecessorException(this);
      }
    }
  }

  public void addPredecessorException(BasicBlock block) {
    predecessorExceptions.add(block);
  }

  public void removePredecessorException(BasicBlock block) {
    while (predecessorExceptions.remove(block)) /**/;
  }

  public void addSuccessorException(BasicBlock block) {
    if (!successorExceptions.contains(block)) {
      successorExceptions.add(block);
      block.addPredecessorException(this);
    }
  }

  public void removeSuccessorException(BasicBlock block) {
    while (successorExceptions.remove(block)) /**/;
    block.removePredecessorException(this);
  }

  public boolean isSuccessor(BasicBlock block) {
    return successors.stream().anyMatch(successor -> successor.id == block.id);
  }

  public List<Integer> getOriginalOffsets() {
    return originalOffsets;
  }

  public InstructionSequence getSeq() {
    return seq;
  }

  public List<BasicBlock> getPredecessors() {
    return predecessors;
  }

  public List<BasicBlock> getSuccessors() {
    return successors;
  }

  public List<BasicBlock> getPredecessorExceptions() {
    return predecessorExceptions;
  }

  public List<BasicBlock> getSuccessorExceptions() {
    return successorExceptions;
  }

  @Override
  public List<? extends IGraphNode> getPredecessorNodes() {
    List<BasicBlock> lst = new ArrayList<>(predecessors);
    lst.addAll(predecessorExceptions);
    return lst;
  }

  @Override
  public String toString() {
    return id + ":" + DecompilerContext.getNewLineSeparator() + seq.toString(0);
  }
}
