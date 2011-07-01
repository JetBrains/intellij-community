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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Arrays;

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
        if (context.getCompletionChar() != '(' && TypesUtil.isClassType(parameters[0].getType(), GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
          document.insertString(offset, " {}");
          caretModel.moveToOffset(offset + 2);
          return;
        }
      }

      PsiDocumentManager docManager = PsiDocumentManager.getInstance(method.getProject());
      docManager.commitDocument(document);
      /*
      //always use parentheses

      PsiFile psiFile = docManager.getPsiFile(document);
      if (method.getParameterList().getParametersCount() > 0 && isExpressionStatement(psiFile, context.getStartOffset())) {
        return;
      }*/

      new MethodParenthesesHandler(method, true).handleInsert(context, item);
      return;
    }

    if (obj instanceof String) {
      Editor editor = context.getEditor();
      Document document = editor.getDocument();
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        handleOverwrite(editor.getCaretModel().getOffset(), document);
      }
      else if (context.getCompletionChar() == ' ') {
        context.setAddCompletionChar(false);
      }
    } else if (obj instanceof PsiClass) {
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
        final PsiMethod[] methods = ResolveUtil.getAllClassConstructors(clazz, (GroovyPsiElement)parent, PsiSubstitutor.EMPTY);
        for (PsiMethod method : methods) {
          if (method.getParameterList().getParameters().length > 0) {
            caretModel.moveToOffset(offset + 1);
            return;
          }
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

    addTailType(item).processTail(context.getEditor(), context.getTailOffset());
  }

  private static void handleOverwrite(final int offset, final Document document) {
    final CharSequence sequence = document.getCharsSequence();
    int i = offset;
    while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
    document.deleteString(offset, i);
  }

  private static TailType addTailType(LookupElement item) {
    if ("default".equals(item.toString())) {
      return TailType.CASE_COLON;
    }
    @NonNls String[] withSpace =
      {"private", "public", "protected", "static", "transient", "abstract", "native", "volatile", "strictfp", "boolean", "byte", "char",
        "short", "int", "float", "long", "double", "void", "new", "try", "while", "with", "switch", "for", "return", "throw", "throws",
        "assert", "synchronized", "package", "class", "interface", "enum", "extends", "implements", "case", "catch", "finally", "else",
        "instanceof", "import", "final", "def"};
    if (Arrays.asList(withSpace).contains(item.toString())) {
      return TailType.SPACE;
    }
    return TailType.NONE;
  }
}
