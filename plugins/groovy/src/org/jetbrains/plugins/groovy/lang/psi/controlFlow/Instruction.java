package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.Nullable;

import java.util.Stack;

import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public interface Instruction {
  Iterable<? extends Instruction> succ();
  Iterable<? extends Instruction> pred();

  int num();

  @Nullable
  PsiElement getElement();
}
