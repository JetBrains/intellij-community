/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

public class GrUnnecessaryPublicModifierInspection extends GroovySuppressableInspectionTool {

  private static final LocalQuickFix FIX = new GrRemoveModifierFix(PsiModifier.PUBLIC);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement modifier) {
        if (modifier.getNode().getElementType() != GroovyTokenTypes.kPUBLIC) return;

        PsiElement list = modifier.getParent();
        if (!(list instanceof GrModifierList)) return;

        PsiElement parent = list.getParent();
        // Do not mark public on fields as unnecessary
        // It may be put there explicitly to prevent getter/setter generation.
        if (parent instanceof GrVariableDeclaration) return;

        holder.registerProblem(
          modifier,
          GroovyInspectionBundle.message("unnecessary.modifier.description", PsiModifier.PUBLIC),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          FIX
        );
      }
    };
  }
}
