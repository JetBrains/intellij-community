package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.codeInsight.documentation.render.DocRenderManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JourneyEditorFactory {
  @RequiresEdt
  public static Editor createEditor(Project project, Document document, @NotNull VirtualFile file) {
    EditorImpl editor = (EditorImpl)EditorFactory.getInstance().createEditor(document, project, file, false);
    JScrollPane scrollPane = editor.getScrollPane();
    editor.setBorder(JBUI.Borders.empty());
    editor.getMarkupModel().removeAllHighlighters();
    editor.getGutterComponentEx().setPaintBackground(false);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setWheelScrollingEnabled(true);
    scrollPane.setAutoscrolls(true);
    scrollPane.setBorder(JBUI.Borders.empty());
    /*
     Rendered docs cannot be resized along with font size,
     which leads to a small code text and giant Javadocs.
     Can turn it back when rendered docs support font size change.
    */
    DocRenderManager.setDocRenderingEnabled(editor, false);
    return editor;
  }
}
