/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandlerFactory;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;

import java.util.Arrays;

/**
 * @author ven
 */
public class GroovyInsertHandler extends DefaultInsertHandler {
  public void handleInsert(CompletionContext context, int startOffset, LookupData data, LookupItem item, boolean signatureSelected, char completionChar) {
    Object obj = item.getObject();
    if (obj instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) obj;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      Editor editor = context.editor;
      Document document = editor.getDocument();
      if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
        handleOverwrite(editor.getCaretModel().getOffset(), document);
      }

      if (startOffset > 0 && document.getCharsSequence().charAt(startOffset - 1) == '&') return;   //closure creation
      CaretModel caretModel = editor.getCaretModel();
      int offset = startOffset + method.getName().length();
      if (parameters.length == 0 || parameters.length > 1) {
        if (document.getCharsSequence().charAt(offset) != '(') {
          document.insertString(offset, "()");
        }
        if (parameters.length > 0) {
          caretModel.moveToOffset(offset + 1);
        } else {
          caretModel.moveToOffset(offset + 2);
        }
      } else {
        PsiType paramType = parameters[0].getType();
        if (paramType.getCanonicalText().equals("groovy.lang.Closure")) {
          document.insertString(offset, " {}");
          caretModel.moveToOffset(offset + 2);
        }
      }
      return;
    }

    addTailType(item);
    super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);

  }

  private void handleOverwrite(final int offset, final Document document) {
    final CharSequence sequence = document.getCharsSequence();
    int i = offset;
    while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
    document.deleteString(offset, i);
  }

  private void addTailType(LookupItem item) {
    if ("default".equals(item.toString())) {
      item.setTailType(TailType.CASE_COLON);
      return;
    }
    String[] exprs = {"true", "false", "null", "super", "this"};
    String[] withSemi = {"break", "continue"};
    String[] withSpace = {"private", "public", "protected", "static", "transient", "abstract",
        "native", "volatile", "strictfp", "boolean", "byte", "char", "short", "int", "float", "long", "double", "void",
        "new", "try", "while", "with", "switch", "for", "return", "throw", "thros", "assert", "synchronized", "package",
        "class", "interface", "enum", "extends", "implements", "case", "catch", "finally", "else", "instanceof",
        "import", "final",};
    if (Arrays.asList(withSemi).contains(item.toString())) {
      item.setTailType(TailType.SEMICOLON);
      return;
    }
    if (Arrays.asList(exprs).contains(item.toString())) {
      item.setTailType(TailType.NONE);
      return;
    }
    if (Arrays.asList(withSpace).contains(item.toString())) {
      item.setTailType(TailType.SPACE);
      return;
    }
    item.setTailType(TailType.NONE);
  }
}
