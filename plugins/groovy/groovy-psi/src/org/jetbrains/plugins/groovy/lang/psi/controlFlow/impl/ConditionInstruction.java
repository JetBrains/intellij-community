/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class ConditionInstruction extends InstructionImpl implements Instruction {

  private final Set<ConditionInstruction> myDependent;

  public ConditionInstruction(@NotNull PsiElement element, @NotNull Collection<ConditionInstruction> dependent) {
    super(element);
    myDependent = new LinkedHashSet<>(dependent);
    myDependent.add(this);
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    StringBuilder builder = new StringBuilder();
    builder.append("Condition ").append(getElement());
    if (myDependent.size() > 1) {
      builder.append(", dependent: ");
      builder.append(StringUtil.join(ContainerUtil.filter(myDependent, d -> d != this), i -> String.valueOf(i.num()), ", "));
    }
    return builder.toString();
  }

  public Set<ConditionInstruction> getDependentConditions() {
    return myDependent;
  }
}
