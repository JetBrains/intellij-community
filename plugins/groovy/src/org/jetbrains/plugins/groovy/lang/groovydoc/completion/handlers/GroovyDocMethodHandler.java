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

package org.jetbrains.plugins.groovy.lang.groovydoc.completion.handlers;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.plugins.groovy.extensions.completion.ContextSpecificInsertHandler;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GroovyDocMethodHandler implements ContextSpecificInsertHandler {

  public boolean isAcceptable(InsertionContext context, int startOffset, LookupElement item) {
    PsiFile file = context.getFile();
    if (!(file instanceof GroovyFile)) return false;

    PsiElement element = file.findElementAt(startOffset);
    while (element != null && !(element instanceof PsiFile) && !(element instanceof GrDocComment)) {
      element = element.getParent();
    }
    if (!(element instanceof GrDocComment)) return false;

    int offset = context.getEditor().getCaretModel().getOffset();
    String text = file.getText();
    return offset < text.length() && item.getObject() instanceof PsiMethod;

  }

  public void handleInsert(InsertionContext context, int startOffset, LookupElement item) {

    Editor editor = context.getEditor();
    Object o = item.getObject();
    assert o instanceof PsiMethod;
    PsiMethod method = (PsiMethod) o;

    StringBuffer buffer = new StringBuffer();
    buffer.append("(");
    PsiParameterList params = method.getParameterList();
    int count = params.getParametersCount();
    int i = 0;
    for (PsiParameter parameter : params.getParameters()) {
      PsiType type = parameter.getType();
      String text = type.getCanonicalText();
      if (type instanceof PsiEllipsisType) {
        text = text.substring(0, text.length() - 3) + "[]";
      }
      buffer.append(PsiTypesUtil.unboxIfPossible(text));
      if (i < count - 1) {
        buffer.append(", ");
      }
      i++;
    }
    buffer.append(") ");

    CaretModel caretModel = editor.getCaretModel();
    int endOffset = shortenParamterReferences(context, startOffset, method, buffer);

    caretModel.moveToOffset(endOffset);
  }

  private static int shortenParamterReferences(InsertionContext context, int startOffset, PsiMethod method, StringBuffer buffer) {
    Document document = context.getEditor().getDocument();
    int offset = startOffset + method.getName().length();
    String paramText = buffer.toString();
    document.insertString(offset, paramText);
    int endOffset = offset + paramText.length();

    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    PsiReference ref = context.getFile().findReferenceAt(startOffset);
    if (ref instanceof GrDocMethodReference) {
      GrDocMethodReference methodReference = (GrDocMethodReference) ref;
      GrDocMethodParams list = methodReference.getParameterList();
      for (GrDocMethodParameter parameter : list.getParameters()) {
        PsiUtil.shortenReferences(parameter);
      }
      endOffset = methodReference.getTextRange().getEndOffset() + 1;
    }
    return endOffset;
  }


}
