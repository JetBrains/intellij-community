package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JourneyEditorFactory {
  @RequiresEdt
  public static Editor createEditor(Project project, Document document, @NotNull VirtualFile file) {
    EditorImpl editor = (EditorImpl)EditorFactory.getInstance().createEditor(document, project, file, false);
    JScrollPane scrollPane = editor.getScrollPane();
    editor.setBorder(JBUI.Borders.empty());
    editor.getMarkupModel().removeAllHighlighters();
    editor.getGutterComponentEx().setPaintBackground(false);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setWheelScrollingEnabled(true);
    scrollPane.setAutoscrolls(true);
    scrollPane.setBorder(JBUI.Borders.empty());
    return editor;
  }

  public static @Nullable Editor stealEditor(PsiElement psiElement) {
    VirtualFile virtualFile = psiElement.getContainingFile().getViewProvider().getVirtualFile();
    Project project = psiElement.getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, psiElement.getTextRange().getStartOffset());
    descriptor = descriptor.setUseCurrentWindow(false);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    return fileEditorManager.openTextEditor(descriptor, false);
  }
}
