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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TextViewer extends EditorTextField {
  private final boolean myEmbeddedIntoDialogWrapper;

  public TextViewer(@NotNull String initialText, @NotNull Project project, boolean viewer) {
    this(createDocument(initialText, viewer), project, true, viewer);
  }

  public TextViewer(@NotNull String initialText, @NotNull Project project) {
    this(initialText, project, true);
  }

  public TextViewer(@NotNull Document document, @NotNull Project project, boolean embeddedIntoDialogWrapper, boolean viewer) {
    super(document, project, FileTypes.PLAIN_TEXT, viewer, false);

    myEmbeddedIntoDialogWrapper = embeddedIntoDialogWrapper;
    setFontInheritedFromLAF(false);
  }

  private static Document createDocument(@NotNull String initialText, boolean viewer) {
    if (needSlashRSupport(initialText, viewer)){
      return ((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(initialText, true, false);
    }
    else {
      return EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(initialText));
    }
  }

  @Override
  public void setText(@Nullable String text) {
    if (text != null) {
      if (needSlashRSupport(text, isViewer())) {
        if (!((DocumentImpl)getDocument()).setAcceptSlashR(true)) {
          Editor editor = getEditor();
          if (editor instanceof EditorEx) {
            ((EditorEx)editor).reinitSettings();
          }
        }
      }
      else {
        text = StringUtil.convertLineSeparators(text);
      }
    }
    super.setText(text);
  }

  private static boolean needSlashRSupport(String text, boolean viewer) {
    return !viewer && text.contains("\r");
  }

  @Override
  protected @NotNull EditorEx createEditor() {
    final EditorEx editor = super.createEditor();
    editor.setHorizontalScrollbarVisible(true);
    editor.setCaretEnabled(true);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    editor.setEmbeddedIntoDialogWrapper(myEmbeddedIntoDialogWrapper);
    editor.getComponent().setPreferredSize(null);
    editor.getSettings().setUseSoftWraps(true);

    editor.setColorsScheme(DebuggerUIUtil.getColorScheme());
    return editor;
  }
}