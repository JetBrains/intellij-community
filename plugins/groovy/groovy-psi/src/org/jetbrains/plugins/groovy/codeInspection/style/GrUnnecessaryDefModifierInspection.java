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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GrUnnecessaryDefModifierInspection extends GroovySuppressableInspectionTool {

  private static final GrModifierFix FIX = new GrRemoveModifierFix(GrModifier.DEF);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitModifierList(GrModifierList modifierList) {
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof GrMethod) && !(parent instanceof GrParameter) && !(parent instanceof GrVariableDeclaration)) return;

        PsiElement modifier = modifierList.getModifier(GrModifier.DEF);
        if (modifier == null) return;

        if (parent instanceof GrMethod && ((GrMethod)parent).getReturnTypeElementGroovy() != null ||
            parent instanceof GrVariable && ((GrVariable)parent).getTypeElementGroovy() != null ||
            parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getTypeElementGroovy() != null) {
          holder.registerProblem(
            modifier,
            GroovyInspectionBundle.message("unnecessary.modifier.description", GrModifier.DEF),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            FIX
          );
        }
      }
    });
  }
}
