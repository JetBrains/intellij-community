/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;

/**
 * @author Maxim.Medvedev
 */
public class GrMethodMergingContributor extends CompletionContributor {
  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    final CompletionParameters parameters = context.getParameters();

    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) {
      return null;
    }

    boolean needInsertBrace = false;
    boolean needInsertParenth = false;

    final LookupElement[] items = context.getItems();
    if (items.length > 1) {
      String commonName = null;
      final ArrayList<PsiMethod> allMethods = new ArrayList<>();
      for (LookupElement item : items) {
        Object o = item.getPsiElement();
        if (item.getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        final PsiMethod method = (PsiMethod)o;
        final JavaChainLookupElement chain = item.as(JavaChainLookupElement.CLASS_CONDITION_KEY);
        final String name = method.getName() + "#" + (chain == null ? "" : chain.getQualifier().getLookupString());

        if (commonName != null && !commonName.equals(name)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        if (hasOnlyClosureParams(method)) {
          needInsertBrace = true;
        }
        else {
          needInsertParenth = true;
        }

        if (needInsertBrace && needInsertParenth) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        commonName = name;
        allMethods.add(method);
      }
      for (LookupElement item : items) {
        JavaCompletionUtil.putAllMethods(item, allMethods);
      }

      return AutoCompletionDecision.insertItem(JavaMethodMergingContributor.findBestOverload(items));
    }

    return super.handleAutoCompletionPossibility(context);

  }

  private static boolean hasOnlyClosureParams(PsiMethod method) {
    final PsiParameter[] params = method.getParameterList().getParameters();
    for (PsiParameter param : params) {
      final PsiType type = param.getType();
      if (!TypesUtil.isClassType(type, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
        return false;
      }
    }
    return params.length > 0;
  }
}
