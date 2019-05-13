// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  private final boolean myNegated;
  private final Set<ConditionInstruction> myDependent;

  public ConditionInstruction(@NotNull PsiElement element, boolean negated, @NotNull Collection<ConditionInstruction> dependent) {
    super(element);
    myNegated = negated;
    myDependent = new LinkedHashSet<>(dependent);
    myDependent.add(this);
  }

  public boolean isNegated() {
    return myNegated;
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    StringBuilder builder = new StringBuilder();
    builder.append("Condition ").append(getElement());
    if (myNegated) builder.append(", negated");
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
