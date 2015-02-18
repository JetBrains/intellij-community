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
package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import java.util.List;

public class GroovyMethodOverrideHandler implements InsertHandler<LookupElement> {

  private final PsiClass myPsiClass;

  public GroovyMethodOverrideHandler(PsiClass aClass) {
    this.myPsiClass = aClass;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    PsiMethod method = (PsiMethod)item.getObject();
    List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(myPsiClass, method, false);
    context.commitDocument();
    GenerateMembersUtil.insertMembersAtOffset(context.getFile(), context.getStartOffset(),
                                              OverrideImplementUtil.convert2GenerationInfos(prototypes));
  }
}
