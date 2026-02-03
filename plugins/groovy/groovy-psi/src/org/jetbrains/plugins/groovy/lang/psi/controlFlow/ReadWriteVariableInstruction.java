// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

public class ReadWriteVariableInstruction extends InstructionImpl {
  public static final ReadWriteVariableInstruction[] EMPTY_ARRAY = new ReadWriteVariableInstruction[0];

  public static final int WRITE = -1;
  public static final int READ = 1;

  private final boolean myIsWrite;
  private final int myDescriptor;

  public ReadWriteVariableInstruction(int varName, PsiElement element, int accessType) {
    super(element);
    myDescriptor = varName;
    myIsWrite = accessType == WRITE;
  }

  public int getDescriptor() {
    return myDescriptor;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myDescriptor;
  }
}
