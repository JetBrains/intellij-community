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

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class GroovyInsertHandler implements InsertHandler<LookupElement> {
  public static final GroovyInsertHandler INSTANCE = new GroovyInsertHandler();

  public void handleInsert(InsertionContext context, LookupElement item) {
    @NonNls Object obj = item.getObject();

    if (obj instanceof GroovyResolveResult) {
      obj = ((GroovyResolveResult)obj).getElement();
    }

    if (obj instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)obj;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      Editor editor = context.getEditor();
      Document document = editor.getDocument();
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        handleOverwrite(editor.getCaretModel().getOffset(), document);
      }

      CaretModel caretModel = editor.getCaretModel();
      int offset = context.getStartOffset() + method.getName().length();
      PsiFile file = PsiDocumentManager.getInstance(method.getProject()).getPsiFile(document);
      assert file != null;
      PsiElement elementAt = file.findElementAt(context.getStartOffset());
      assert elementAt != null;
      PsiElement parent = elementAt.getParent();
      if (parent instanceof GrReferenceExpression &&
          ((GrReferenceExpression)parent).getDotTokenType() == GroovyElementTypes.mMEMBER_POINTER) {
        return;
      }

      if (parent instanceof GrAnnotationNameValuePair || parent != null && parent.getParent() instanceof GrAnnotationNameValuePair) {
        document.insertString(offset, " = ");
        caretModel.moveToOffset(offset + 3);
        return;
      }

      if (PsiTreeUtil.getParentOfType(elementAt, GrImportStatement.class) != null) return;

      if (parameters.length == 1) {
        if ((context.getCompletionChar() != '(' && context.getCompletionChar() != ' ') && 
            TypesUtil.isClassType(parameters[0].getType(), GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
          int afterBrace;
          final int nonWs = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
          if (nonWs < document.getTextLength() && document.getCharsSequence().charAt(nonWs) == '{') {
            afterBrace = nonWs + 1;
          } else {
            document.insertString(offset, " {}");
            afterBrace = offset + 2;
          }
          caretModel.moveToOffset(afterBrace);
          return;
        }
      }

      context.commitDocument();

      if (context.getCompletionChar() == ' ') {
        GrExpression expr = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getStartOffset()), GrExpression.class);
        if (expr != null && PsiUtil.isExpressionStatement(expr)) {
          return;
        }
      }

      new MethodParenthesesHandler(method, true).handleInsert(context, item);
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(editor, method);
      return;
    }

    if (obj instanceof PsiClass) {
      final PsiClass clazz = (PsiClass)obj;
      Editor editor = context.getEditor();
      Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(clazz.getProject()).getPsiFile(document);
      assert file != null;
      PsiElement elementAt = file.findElementAt(context.getStartOffset());
      assert elementAt != null;
      CaretModel caretModel = editor.getCaretModel();
      int offset = context.getStartOffset() + elementAt.getTextLength();

      final String text = document.getText();
      final PsiElement parent = elementAt.getParent();
      if (parent instanceof GrCodeReferenceElement &&
          parent.getParent() instanceof GrNewExpression &&
          (offset == text.length() || !text.substring(offset).trim().startsWith("("))) {
        document.insertString(offset, "()");
        if (GroovyCompletionUtil.hasConstructorParameters(clazz, (GroovyPsiElement)parent)) {
          caretModel.moveToOffset(offset + 1);
          return;
        }
        caretModel.moveToOffset(offset + 2);
        return;
      }
    }

    if (context.getCompletionChar() == '=') {
      context.setAddCompletionChar(false);
      TailType.EQ.processTail(context.getEditor(), context.getTailOffset());
      return;
    }

    if (obj instanceof PsiPackage) {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor(), null);
    }
  }

  private static void handleOverwrite(final int offset, final Document document) {
    final CharSequence sequence = document.getCharsSequence();
    int i = offset;
    while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
    document.deleteString(offset, i);
  }

}
