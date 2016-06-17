/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AddBlockInlayAction extends EditorAction {
  public AddBlockInlayAction() {
    super(new EditorActionHandler() {
      @Override
      protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
        JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, "");
        pane.setEditorKit(UIUtil.getHTMLEditorKit(false));
        pane.setText("<html><body>Hello <b>there</b>!</body></html>");
        pane.setBackground(HintUtil.INFORMATION_COLOR);
        pane.setBorder(BorderFactory.createLineBorder(Color.gray));
        int width = editor.getSettings().getRightMargin(editor.getProject()) * EditorUtil.getPlainSpaceWidth(editor);
        pane.setSize(width, Integer.MAX_VALUE);
        int height = pane.getPreferredSize().height;
        pane.setSize(width, height);
        editor.getInlayModel().addElement(editor.getCaretModel().getOffset(), Inlay.Type.BLOCK, new Inlay.Renderer() {
          @Override
          public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
            Graphics localG = g.create(r.x, r.y, r.width, r.height);
            pane.paint(localG);
            localG.dispose();
          }

          @Override
          public int calcHeightInPixels(@NotNull Editor editor) {
            return height;
          }

          @Override
          public int calcWidthInPixels(@NotNull Editor editor) {
            return width;
          }
        });
      }
    });
  }
}
