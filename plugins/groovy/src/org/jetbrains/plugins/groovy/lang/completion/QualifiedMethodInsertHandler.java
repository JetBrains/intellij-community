/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaGlobalMemberLookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
* Created by Max Medvedev on 14/05/14
*/
class QualifiedMethodInsertHandler implements InsertHandler<JavaGlobalMemberLookupElement> {
  public static final InsertHandler<JavaGlobalMemberLookupElement> INSTANCE = new QualifiedMethodInsertHandler();

  private QualifiedMethodInsertHandler() {
  }

  @Override
  public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
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
