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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Producer;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 * @since May 13, 2002
 */
public class SimplePasteAction extends EditorAction {
  public SimplePasteAction() {
    super(new Handler());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(presentation.isEnabled());
    }
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      Producer<Transferable> producer = PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext);
      if (!editor.getCaretModel().supportsMultipleCarets() && editor.isColumnMode()) {
        EditorModificationUtil.pasteTransferableAsBlock(editor, producer);
      }
      else {
        TextRange range = EditorModificationUtil.pasteTransferable(editor, producer);
        editor.putUserData(EditorEx.LAST_PASTED_REGION, range);
      }
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isViewer();
    }
  }
}
