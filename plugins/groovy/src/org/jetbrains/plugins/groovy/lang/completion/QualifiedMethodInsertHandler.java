// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaGlobalMemberLookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

final class QualifiedMethodInsertHandler implements InsertHandler<JavaGlobalMemberLookupElement> {
  public static final InsertHandler<JavaGlobalMemberLookupElement> INSTANCE = new QualifiedMethodInsertHandler();

  private QualifiedMethodInsertHandler() {
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull JavaGlobalMemberLookupElement item) {
    GroovyInsertHandler.INSTANCE.handleInsert(context, item);
    final PsiClass containingClass = item.getContainingClass();
    context.getDocument().insertString(context.getStartOffset(), containingClass.getName() + ".");
    PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
    final GrReferenceExpression ref = PsiTreeUtil
      .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);
    if (ref != null) {
      ref.bindToElement(containingClass);
    }
  }
}
