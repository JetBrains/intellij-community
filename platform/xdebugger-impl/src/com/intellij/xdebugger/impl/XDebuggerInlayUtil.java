/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class XDebuggerInlayUtil {
  public static void createInlay(@NotNull Project project, @NotNull VirtualFile file, int offset, String inlayText) {
    UIUtil.invokeLaterIfNeeded(() -> {
      FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
      if (editor instanceof TextEditor) {
        Editor e = ((TextEditor)editor).getEditor();
        CharSequence text = e.getDocument().getImmutableCharSequence();

        int insertOffset = offset;
        while (insertOffset < text.length() && Character.isJavaIdentifierPart(text.charAt(insertOffset))) insertOffset++;

        List<Inlay> existing = e.getInlayModel().getInlineElementsInRange(insertOffset, insertOffset);
        for (Inlay inlay : existing) {
          if (inlay.getRenderer() instanceof MyRenderer) {
            Disposer.dispose(inlay);
          }
        }

        e.getInlayModel().addInlineElement(insertOffset, new MyRenderer(inlayText));
      }
    });
  }

  public static void clearInlays(@NotNull Project project) {
    UIUtil.invokeLaterIfNeeded(() -> {
      FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          Editor e = ((TextEditor)editor).getEditor();
          List<Inlay> existing = e.getInlayModel().getInlineElementsInRange(0, e.getDocument().getTextLength());
          for (Inlay inlay : existing) {
            if (inlay.getRenderer() instanceof MyRenderer) {
              Disposer.dispose(inlay);
            }
          }
        }
      }
    });
  }

  private static class MyRenderer implements EditorCustomElementRenderer {
    private final String myText;

    private MyRenderer(String text) {
      myText = "(" + text + ")";
    }

    private static FontInfo getFontInfo(@NotNull Editor editor) {
      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      FontPreferences fontPreferences = colorsScheme.getFontPreferences();
      TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      int fontStyle = attributes == null ? Font.PLAIN : attributes.getFontType();
      return ComplementaryFontsRegistry.getFontAbleToDisplay('a', fontStyle, fontPreferences,
                                                             FontInfo.getFontRenderContext(editor.getContentComponent()));
    }

    @Override
    public int calcWidthInPixels(@NotNull Editor editor) {
      FontInfo fontInfo = getFontInfo(editor);
      return fontInfo.fontMetrics().stringWidth(myText);
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      if (attributes == null) return;
      Color fgColor = attributes.getForegroundColor();
      if (fgColor == null) return;
      g.setColor(fgColor);
      FontInfo fontInfo = getFontInfo(editor);
      g.setFont(fontInfo.getFont());
      FontMetrics metrics = fontInfo.fontMetrics();
      g.drawString(myText, r.x, r.y + metrics.getAscent());
    }
  }
}
