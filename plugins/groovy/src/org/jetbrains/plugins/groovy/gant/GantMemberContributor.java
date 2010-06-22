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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class GantMemberContributor implements NonCodeMembersProcessor {

  @Override
  public boolean processNonCodeMembers(PsiType type, PsiScopeProcessor processor, PsiElement place, boolean forCompletion) {
    if (type.equalsToText("groovy.util.AntBuilder")) {
      return processAntTasks(processor, place);
    }

    if (!(place instanceof GrReferenceExpression) || ((GrReferenceExpression)place).isQualified()) {
      return true;
    }

    PsiFile file = place.getContainingFile();
    if (!GantUtils.isGantScriptFile(file)) {
      return true;
    }

    final GrClosableBlock closure = PsiTreeUtil.getContextOfType(place, GrClosableBlock.class, true);
    if (closure == null) {
      return true;
    }

    for (GrArgumentLabel label : GantUtils.getScriptTargets((GroovyFile)file)) {
      final String targetName = label.getName();
      if (targetName != null) {
        final PsiNamedElement variable = new LightVariableBuilder(targetName, GrClosableBlock.GROOVY_LANG_CLOSURE, label).
          setBaseIcon(GantIcons.GANT_TARGET);
        if (!ResolveUtil.processElement(processor, variable)) {
          return false;
        }
      }
    }

    return processAntTasks(processor, place);

  }

  private static boolean processAntTasks(PsiScopeProcessor processor, PsiElement place) {
    for (LightMethodBuilder task : AntTasksProvider.getInstance(place.getProject()).getAntTasks()) {
      if (!ResolveUtil.processElement(processor, task)) {
        return false;
      }
    }
    return true;
  }

}


