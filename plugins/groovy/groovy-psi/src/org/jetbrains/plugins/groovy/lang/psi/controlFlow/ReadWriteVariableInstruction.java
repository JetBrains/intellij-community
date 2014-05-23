/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final String myName;

  public ReadWriteVariableInstruction(@NotNull String varName, PsiElement element, int accessType) {
    super(element);
    myName = varName;
    myIsWrite = accessType == WRITE;
  }

  @NotNull public String getVariableName() {
    return myName;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  @Override
  protected String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myName;
  }
}
