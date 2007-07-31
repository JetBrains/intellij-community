package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
class InstructionImpl implements Instruction {
  List<Instruction> myPred = new ArrayList<Instruction>();

  List<Instruction> mySucc = new ArrayList<Instruction>();

  PsiElement myPsiElement;

  public PsiElement getElement() {
    return myPsiElement;
  }

  InstructionImpl(PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  public Iterable<Instruction> succ() {
    return mySucc;
  }

  public Iterable<Instruction> pred() {
    return myPred;
  }
}
