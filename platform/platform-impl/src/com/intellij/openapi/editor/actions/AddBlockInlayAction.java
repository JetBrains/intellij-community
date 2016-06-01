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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class AddBlockInlayAction extends EditorAction {
  public AddBlockInlayAction() {
    super(new EditorActionHandler() {
      @Override
      protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
        editor.getInlayModel().addElement(editor.getCaretModel().getOffset(), Inlay.Type.BLOCK, new Inlay.Renderer() {
          @Override
          public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
            g.setColor(Color.pink);
            g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 5, 5);
          }

          @Override
          public int calcHeightInPixels(@NotNull Editor editor) {
            return 10;
          }

          @Override
          public int calcWidthInPixels(@NotNull Editor editor) {
            return 500;
          }
        });
      }
    });
  }
}
