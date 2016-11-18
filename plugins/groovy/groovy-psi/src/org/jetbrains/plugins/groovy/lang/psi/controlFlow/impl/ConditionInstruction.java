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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class ConditionInstruction extends InstructionImpl implements Instruction {

  private final Set<ConditionInstruction> myDependent;

  public ConditionInstruction(@NotNull PsiElement element, @NotNull Collection<ConditionInstruction> dependent) {
    super(element);
    myDependent = new HashSet<>(dependent);
    myDependent.add(this);
  }

  @Override
  protected String getElementPresentation() {
    return "Condition " + getElement();
  }

  public Set<ConditionInstruction> getDependentConditions() {
    return myDependent;
  }
}
