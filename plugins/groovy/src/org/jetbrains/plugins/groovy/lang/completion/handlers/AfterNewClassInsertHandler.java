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

package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionFeatures;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Maxim.Medvedev
 */
public class AfterNewClassInsertHandler implements InsertHandler<LookupItem<PsiClassType>> {
  private final PsiClassType myClassType;
  private final boolean myTriggerFeature;

  public AfterNewClassInsertHandler(PsiClassType classType, boolean triggerFeature) {
    myClassType = classType;
    myTriggerFeature = triggerFeature;
  }

  public void handleInsert(InsertionContext context, LookupItem<PsiClassType> item) {
    final PsiClassType.ClassResolveResult resolveResult = myClassType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null || !psiClass.isValid()) {
      return;
    }
    final GroovyPsiElement place = obtainPlace(context);
    PsiMethod[] constructors = ResolveUtil.getAllClassConstructors(psiClass, place, resolveResult.getSubstitutor());
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper();
    boolean hasParams = ContainerUtil.or(constructors, new Condition<PsiMethod>() {
      public boolean value(PsiMethod psiMethod) {
        if (!resolveHelper.isAccessible(psiMethod, place, null)) {
          return false;
        }

        return psiMethod.getParameterList().getParametersCount() > 0;
      }
    });

    if (myTriggerFeature) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }

    if (hasParams) {
      ParenthesesInsertHandler.WITH_PARAMETERS.handleInsert(context, item);
    }
    else {
      ParenthesesInsertHandler.NO_PARAMETERS.handleInsert(context, item);
    }
    GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
    if (hasParams) AutoPopupController.getInstance(constructors[0].getProject()).autoPopupParameterInfo(context.getEditor(), null);
  }

  private static GroovyPsiElement obtainPlace(InsertionContext context) {
    PsiElement place = context.getFile().findElementAt(context.getStartOffset());
    assert place != null;
    if (place instanceof GroovyPsiElement) {
      return (GroovyPsiElement)place;
    }
    return (GroovyFileBase)place.getContainingFile();
  }
}
