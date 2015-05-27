/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.flow.visitor.GrContractChecker;
import org.jetbrains.plugins.groovy.lang.flow.visitor.GrStandardInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collections;
import java.util.Map;

public class GrContractInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(
      new GroovyElementVisitor() {

        @Override
        public void visitMethod(GrMethod method) {
          for (MethodContract contract : ControlFlowAnalyzer.getMethodContracts(method)) {
            final GrOpenBlock body = method.getBlock();
            if (body == null) continue;

            final GrContractChecker checker = new GrContractChecker(method, contract, isOnTheFly);
            final DfaMemoryState initialState = ContractChecker.createInitialState(method, contract, checker);
            checker.analyzeMethod(body, new GrStandardInstructionVisitor(checker), Collections.singletonList(initialState));

            for (Map.Entry<PsiElement, String> entry : checker.getErrors().entrySet()) {
              PsiElement element = entry.getKey();
              holder.registerProblem(element, entry.getValue());
            }
          }
        }

        @Override
        public void visitAnnotation(GrAnnotation annotation) {
          if (!ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotation.getQualifiedName())) return;
          ContractInspection.checkContractAnnotation(annotation, holder);
        }
      }
    );
  }
}
