package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

public final class TextViewer extends EditorTextField {
  private final boolean myEmbeddedIntoDialogWrapper;
  private final boolean myUseSoftWraps;

  public TextViewer(@NotNull Project project, boolean embeddedIntoDialogWrapper, boolean useSoftWraps) {
    this(createDocument(""), project, embeddedIntoDialogWrapper, useSoftWraps);
  }

  public TextViewer(@NotNull String initialText, @NotNull Project project) {
    this(createDocument(initialText), project, false, false);
  }

  public TextViewer(@NotNull Document document, @NotNull Project project, boolean embeddedIntoDialogWrapper, boolean useSoftWraps) {
    super(document, project, FileTypes.PLAIN_TEXT, true, false);

    myEmbeddedIntoDialogWrapper = embeddedIntoDialogWrapper;
    myUseSoftWraps = useSoftWraps;
  }

  private static Document createDocument(@NotNull String initialText) {
    final Document document = EditorFactory.getInstance().createDocument(initialText);
    if (document instanceof DocumentImpl) {
      ((DocumentImpl)document).setAcceptSlashR(true);
    }
    return document;
  }

  @Override
  protected EditorEx createEditor() {
    final EditorEx editor = super.createEditor();
    editor.setHorizontalScrollbarVisible(true);
    editor.setVerticalScrollbarVisible(true);
    editor.setEmbeddedIntoDialogWrapper(myEmbeddedIntoDialogWrapper);
    editor.getComponent().setPreferredSize(null);
    editor.getSettings().setUseSoftWraps(myUseSoftWraps);
    return editor;
  }
}