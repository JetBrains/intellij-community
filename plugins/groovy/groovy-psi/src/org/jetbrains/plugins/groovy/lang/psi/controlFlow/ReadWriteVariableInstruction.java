// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

/**
 * @author ven
*/
public class ReadWriteVariableInstruction extends InstructionImpl {
  public static final ReadWriteVariableInstruction[] EMPTY_ARRAY = new ReadWriteVariableInstruction[0];

  public static final int WRITE = -1;
  public static final int READ = 1;

  private final boolean myIsWrite;
  private final VariableDescriptor myDescriptor;

  public ReadWriteVariableInstruction(@NotNull VariableDescriptor varName, PsiElement element, int accessType) {
    super(element);
    myDescriptor = varName;
    myIsWrite = accessType == WRITE;
  }

  @NotNull
  public VariableDescriptor getDescriptor() {
    return myDescriptor;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myDescriptor.getName();
  }
}
