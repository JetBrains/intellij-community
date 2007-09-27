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

package org.jetbrains.plugins.grails.lang.gsp.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.grails.lang.gsp.lexer.GspTokenTypesEx;
import org.jetbrains.plugins.grails.lang.gsp.psi.gsp.api.GspFile;

/**
 * @author ilyas
 */
public class GspEnterHandler extends EditorWriteActionHandler {
  private EditorActionHandler myOriginalHandler;

  public GspEnterHandler(EditorActionHandler actionHandler) {
    myOriginalHandler = actionHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleEnter(editor, dataContext) && myOriginalHandler != null) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleEnter(Editor editor, DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    PsiFile file = DataKeys.PSI_FILE.getData(dataContext);
    if (project == null) return false;
    if (!(file instanceof GspFile)) return false;

    int carret = editor.getCaretModel().getOffset();
    if (carret == 0) return false;

    PsiElement element = file.findElementAt(carret - 1);
    if (element != null &&
        element.getNode().getElementType() != GspTokenTypesEx.JSCRIPT_BEGIN) {
      return false;
    }

    String text = editor.getDocument().getText();
    if (handleJspLikeScriptlet(editor, text, carret, dataContext)) {
      GspEditorActionUtil.insertSpacesByIndent(editor, project);
      return true;
    }
    return false;
  }

  private boolean handleJspLikeScriptlet(Editor editor, String text, int carret, DataContext dataContext) {
    if (carret < 2 || text.length() < Math.min(carret - 2, 2)) {
      return false;
    }
    if (text.charAt(carret - 1) == '%' && text.charAt(carret - 2) == '<') {
      int position = carret;
      for (; position < text.length() && isWhiteSpace(text, position); position++) {
      }
      if (position < text.length() &&
          text.charAt(position) == '%') {
        myOriginalHandler.execute(editor, dataContext);
        return true;
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, "%>");
        editor.getCaretModel().moveCaretRelatively(-2, 0, false, false, true);
        myOriginalHandler.execute(editor, dataContext);
        myOriginalHandler.execute(editor, dataContext);
        editor.getCaretModel().moveCaretRelatively(0, -1, false, false, true);
        return true;
      }
    } else {
      return false;
    }
  }

  private static boolean isWhiteSpace(String text, int i) {
    return text.charAt(i) == ' ' ||
        text.charAt(i) == '\t' ||
        text.charAt(i) == '\r' ||
        text.charAt(i) == '\n';
  }
}
