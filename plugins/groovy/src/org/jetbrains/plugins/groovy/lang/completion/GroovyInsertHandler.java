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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author ven
 */
public class GroovyInsertHandler implements InsertHandler<LookupElement> {
  public static final GroovyInsertHandler INSTANCE = new GroovyInsertHandler();

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    @NonNls Object obj = item.getObject();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (obj instanceof GroovyResolveResult) {
      substitutor = ((GroovyResolveResult)obj).getSubstitutor();
      obj = ((GroovyResolveResult)obj).getElement();
    }

    if (obj instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)obj;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      Editor editor = context.getEditor();
      Document document = editor.getDocument();
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        handleOverwrite(editor.getCaretModel().getOffset(), document);
      }

      CaretModel caretModel = editor.getCaretModel();
      int offset = context.getTailOffset();
      PsiFile file = context.getFile();
      PsiElement elementAt = file.findElementAt(context.getStartOffset());
      assert elementAt != null;
      PsiElement parent = elementAt.getParent();
      if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER) {
        return;
      }

      CharSequence charsSequence = document.getCharsSequence();
      if (isAnnotationNameValuePair(obj, parent)) {
        int endOffset = offset;
        if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
          endOffset = CharArrayUtil.shiftForward(charsSequence, offset, " \t");
          if (charsSequence.length() > endOffset && charsSequence.charAt(endOffset) == '=') {
            endOffset++;
            endOffset = CharArrayUtil.shiftForward(charsSequence, endOffset, " \t");
          }
        }
        document.replaceString(offset, endOffset, " = ");
        caretModel.moveToOffset(offset + 3);
        return;
      }

      if (PsiTreeUtil.getParentOfType(elementAt, GrImportStatement.class) != null) return;

      if (parameters.length == 1) {
        if ((context.getCompletionChar() != '(' && context.getCompletionChar() != ' ') &&
            TypesUtil.isClassType(parameters[0].getType(), GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
          int afterBrace;
          final int nonWs = CharArrayUtil.shiftForward(charsSequence, offset, " \t");
          if (nonWs < document.getTextLength() && charsSequence.charAt(nonWs) == '{') {
            afterBrace = nonWs + 1;
          }
          else {
            if (isSpaceBeforeClosure(file)) {
              document.insertString(offset, " ");
              offset++;
            }
            if (ClosureCompleter.runClosureCompletion(context, method, substitutor, document, offset, parent)) return;
            if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
              //smart enter invoked
              document.insertString(offset, "{\n}");
              afterBrace = offset + 1;  //position caret before '{' for smart enter
              context.setTailOffset(afterBrace);
            }
            else {
              document.insertString(offset, "{}");
              afterBrace = offset + 1;
            }
          }
          caretModel.moveToOffset(afterBrace);
          return;
        }
      }

      context.commitDocument();

      if (context.getCompletionChar() == ' ' && MethodParenthesesHandler.hasParams(item, context.getElements(), true, method)) {
        return;
      }


      CommonCodeStyleSettings settings = context.getCodeStyleSettings();
      ParenthesesInsertHandler.getInstance(MethodParenthesesHandler.hasParams(item, context.getElements(), true, method),
                                           settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES,
                                           true, true).handleInsert(context, item);

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
        if (GroovyCompletionUtil.hasConstructorParameters(clazz, parent)) {
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
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }

  private static boolean isSpaceBeforeClosure(PsiFile file) {
    return GroovyCodeStyleSettings.getInstance(file).SPACE_BEFORE_CLOSURE_LBRACE;
  }

  private static boolean isAnnotationNameValuePair(Object obj, PsiElement parent) {
    if (parent instanceof GrAnnotationNameValuePair || parent != null && parent.getParent() instanceof GrAnnotationNameValuePair) {
      if (obj instanceof PsiMethod) {
        PsiClass aClass = ((PsiMethod)obj).getContainingClass();
        if (aClass != null && aClass.isAnnotationType()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void handleOverwrite(final int offset, final Document document) {
    final CharSequence sequence = document.getCharsSequence();
    int i = offset;
    while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
    document.deleteString(offset, i);
  }
}
