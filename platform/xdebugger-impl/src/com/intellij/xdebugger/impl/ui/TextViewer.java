/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TextViewer extends EditorTextField {
  private final boolean myEmbeddedIntoDialogWrapper;
  private final boolean myUseSoftWraps;

  public TextViewer(@NotNull Project project, boolean embeddedIntoDialogWrapper, boolean useSoftWraps) {
    this(createDocument(""), project, embeddedIntoDialogWrapper, useSoftWraps);
  }

  public TextViewer(@NotNull String initialText, @NotNull Project project) {
    this(createDocument(initialText), project, false, true);
  }

  public TextViewer(@NotNull Document document, @NotNull Project project, boolean embeddedIntoDialogWrapper, boolean useSoftWraps) {
    super(document, project, FileTypes.PLAIN_TEXT, true, false);

    myEmbeddedIntoDialogWrapper = embeddedIntoDialogWrapper;
    myUseSoftWraps = useSoftWraps;
    setFontInheritedFromLAF(false);
  }

  private static Document createDocument(@NotNull String initialText) {
    final Document document = EditorFactory.getInstance().createDocument(initialText);
    //if (document instanceof DocumentImpl) {
    //  ((DocumentImpl)document).setAcceptSlashR(true);
    //}
    return document;
  }

  @Override
  public void setText(@Nullable String text) {
    super.setText(text != null ? StringUtil.convertLineSeparators(text) : null);
  }

  @Override
  protected EditorEx createEditor() {
    final EditorEx editor = super.createEditor();
    editor.setHorizontalScrollbarVisible(true);
    editor.setCaretEnabled(true);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    editor.setEmbeddedIntoDialogWrapper(myEmbeddedIntoDialogWrapper);
    editor.getComponent().setPreferredSize(null);
    editor.getSettings().setUseSoftWraps(myUseSoftWraps);

    editor.setColorsScheme(DebuggerUIUtil.getColorScheme());
    return editor;
  }
}