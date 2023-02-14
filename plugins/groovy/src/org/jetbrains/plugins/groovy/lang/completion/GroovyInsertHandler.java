// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.CompletionStyleUtil;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.EqTailType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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
import org.jetbrains.plugins.groovy.lang.sam.SamConversionKt;

public class GroovyInsertHandler implements InsertHandler<LookupElement> {
  public static final GroovyInsertHandler INSTANCE = new GroovyInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    @NonNls Object obj = item.getObject();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (obj instanceof GroovyResolveResult) {
      substitutor = ((GroovyResolveResult)obj).getSubstitutor();
      obj = ((GroovyResolveResult)obj).getElement();
    }

    if (obj instanceof PsiMethod method) {
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
        PsiType type = parameters[0].getType();
        PsiClass clazz = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
        if ((context.getCompletionChar() != '(' && context.getCompletionChar() != ' ') &&
            (TypesUtil.isClassType(parameters[0].getType(), GroovyCommonClassNames.GROOVY_LANG_CLOSURE) || (clazz != null && SamConversionKt.findSingleAbstractSignature(clazz) != null))) {
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

      ThreeState hasParams = MethodParenthesesHandler.overloadsHaveParameters(context.getElements(), method);
      if (context.getCompletionChar() == ' ' && hasParams != ThreeState.NO) {
        return;
      }


      CommonCodeStyleSettings settings = CompletionStyleUtil.getCodeStyleSettings(context);
      ParenthesesInsertHandler.getInstance(hasParams != ThreeState.NO,
                                           settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           hasParams == ThreeState.UNSURE ? settings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES : settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES,
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
      EqTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
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
