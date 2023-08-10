// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant.ant;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gant.GantScriptType;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

public class GantMemberContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (aClass != null && ClassUtil.getSuperClassesWithCache(aClass).containsKey("groovy.util.AntBuilder")) {
      processAntTasks(processor, place, state);
      return;
    }

    if (!(place instanceof GrReferenceExpression) || ((GrReferenceExpression)place).isQualified()) {
      return;
    }

    GrClosableBlock closure = PsiTreeUtil.getContextOfType(place, GrClosableBlock.class, true);

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
    if (file == null || !GroovyScriptUtil.isSpecificScriptFile(file, GantScriptType.INSTANCE)) {
      return;
    }

    if (aClass instanceof GroovyScriptClass) {
      for (GrArgumentLabel label : GantUtils.getScriptTargets((GroovyFile)file)) {
        final String targetName = label.getName();
        if (targetName != null) {
          final PsiNamedElement variable = new LightVariableBuilder(targetName, GroovyCommonClassNames.GROOVY_LANG_CLOSURE, label).
            setBaseIcon(JetgroovyIcons.Groovy.Gant_target);
          if (!ResolveUtil.processElement(processor, variable, state)) {
            return;
          }
        }
      }
    }

    if (!antTasksProcessed) {
      processAntTasks(processor, place, state);
    }
  }

  private static boolean processAntTasks(PsiScopeProcessor processor, PsiElement place, ResolveState state) {
    if (!ResolveUtilKt.shouldProcessMethods(processor)) return true;
    for (LightMethodBuilder task : AntTasksProvider.getAntTasks(place)) {
      if (!ResolveUtil.processElement(processor, task, state)) {
        return false;
      }
    }
    return true;
  }

}


