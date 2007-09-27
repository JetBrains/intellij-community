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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
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

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {

    final Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project != null) {
      PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(new Runnable() {
        public void run() {
          executeWriteActionInner(editor, dataContext);
        }
      });
    } else {
      executeWriteActionInner(editor, dataContext);
    }
  }

  private void executeWriteActionInner(Editor editor, DataContext dataContext) {
    if (!handleEnter(editor, dataContext) &&
        myOriginalHandler != null) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleEnter(Editor editor, DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    PsiFile file = DataKeys.PSI_FILE.getData(dataContext);
    if (project == null) return false;
    if (!(file instanceof GspFile)) return false;

    int carret = editor.getCaretModel().getOffset();
    if (carret < 1) return false;

    final EditorHighlighter highlighter = ((EditorEx) editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(carret - 1);

    if (iterator.getTokenType() != GspTokenTypesEx.JSCRIPT_BEGIN) {
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
      final EditorHighlighter highlighter = ((EditorEx) editor).getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(carret - 1);
      if (!GspEditorActionUtil.checkSciptletSeparatorBalance(iterator)) {
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

}
