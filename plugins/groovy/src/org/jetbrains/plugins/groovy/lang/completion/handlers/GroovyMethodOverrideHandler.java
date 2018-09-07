// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GroovyMethodOverrideHandler implements InsertHandler<LookupElement> {

  private final PsiClass myPsiClass;

  public GroovyMethodOverrideHandler(PsiClass aClass) {
    this.myPsiClass = aClass;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    PsiMethod method = (PsiMethod)item.getObject();
    List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(myPsiClass, method, false);
    context.commitDocument();
    GenerateMembersUtil.insertMembersAtOffset(context.getFile(), context.getStartOffset(),
                                              OverrideImplementUtil.convert2GenerationInfos(prototypes));
  }
}
