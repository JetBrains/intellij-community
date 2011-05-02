/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.unassignedVariable;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
public class UnassignedVariableAccessInspection extends GroovyLocalInspectionBase {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("unassigned.access");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyVariableNotAssigned";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  protected void check(GrControlFlowOwner owner, ProblemsHolder problemsHolder) {
    Instruction[] flow = owner.getControlFlow();
    ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow);
    for (ReadWriteVariableInstruction read : reads) {
      PsiElement element = read.getElement();
      if (element instanceof GroovyPsiElement) {
        String name = read.getVariableName();
        GroovyPsiElement property = ResolveUtil.resolveProperty((GroovyPsiElement) element, name);
        if (property != null && !(property instanceof PsiParameter) && !(property instanceof PsiField) &&
            PsiTreeUtil.isAncestor(owner, property, false)) {
          problemsHolder.registerProblem(element, GroovyInspectionBundle.message("unassigned.access.tooltip", name, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
    }
  }
}
