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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class GantMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (ResolveUtil.isInheritor(qualifierType, "groovy.util.AntBuilder", place.getProject())) {
      processAntTasks(processor, place, state);
      return;
    }

    if (!(place instanceof GrReferenceExpression) || ((GrReferenceExpression)place).isQualified()) {
      return;
    }

    GrClosableBlock closure = PsiTreeUtil.getContextOfType(place, GrClosableBlock.class, true);
    if (closure == null) {
      return;
    }

    boolean antTasksProcessed = false;
    while (closure != null) {
      final PsiElement parent = closure.getParent();
      if (parent instanceof GrMethodCall) {
        final PsiMethod method = ((GrMethodCall)parent).resolveMethod();
        if (method instanceof AntBuilderMethod) {
          antTasksProcessed = true;
          if (!processAntTasks(processor, place, state)) {
            return;
          }
          if (!((AntBuilderMethod)method).processNestedElements(processor)) {
            return;
          }
          break;
        }
      }

      closure = PsiTreeUtil.getContextOfType(closure, GrClosableBlock.class, true);
    }

    // ------- gant-specific

    PsiFile file = place.getContainingFile();
    if (!GantUtils.isGantScriptFile(file)) {
      return;
    }

    for (GrArgumentLabel label : GantUtils.getScriptTargets((GroovyFile)file)) {
      final String targetName = label.getName();
      if (targetName != null) {
        final PsiNamedElement variable = new LightVariableBuilder(targetName, GrClosableBlock.GROOVY_LANG_CLOSURE, label).
          setBaseIcon(GantIcons.GANT_TARGET);
        if (!ResolveUtil.processElement(processor, variable, state)) {
          return;
        }
      }
    }

    if (!antTasksProcessed) {
      processAntTasks(processor, place, state);
    }

  }

  private static boolean processAntTasks(PsiScopeProcessor processor, PsiElement place, ResolveState state) {
    if (!AntTasksProvider.antAvailable) {
      return true;
    }

    for (LightMethodBuilder task : AntTasksProvider.getAntTasks(place)) {
      if (!ResolveUtil.processElement(processor, task, state)) {
        return false;
      }
    }
    return true;
  }

}


