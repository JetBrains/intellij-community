// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.reference.SoftReference;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.testAssistant.vfs.TestDataGroupVirtualFile;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 * @author yole
 */
public class TestDataGroupFileEditor extends UserDataHolderBase implements TextEditor {
  private WeakReference<Splitter> myComponent;
  private final TestDataGroupVirtualFile myFile;
  private final Project myProject;
  private final TextEditor myBeforeEditor;
  private final TextEditor myAfterEditor;

  public TestDataGroupFileEditor(Project project, TestDataGroupVirtualFile file) {
    myFile = file;
    myProject = project;
    myBeforeEditor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file.getBeforeFile());
    myAfterEditor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file.getAfterFile());
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    Splitter result = SoftReference.dereference(myComponent);
    if (result == null) {
      myComponent = new WeakReference<>(result = createComponent());
    }
    return result;
  }

  private Splitter createComponent() {
    Splitter splitter = new OnePixelSplitter(false, 0.5f, 0.1f, 0.9f);
    splitter.setFirstComponent(wrapWithTitle(myFile.getBeforeFile().getName(), myBeforeEditor));
    splitter.setSecondComponent(wrapWithTitle(myFile.getAfterFile().getName(), myAfterEditor));
    return splitter;
  }

  @NotNull
  @Override
  public Editor getEditor() {
    if (SwingUtilities.isEventDispatchThread() && isBeforeEditorFocused()) {
      return myBeforeEditor.getEditor();
    }
    return myAfterEditor.getEditor();
  }

  private boolean isBeforeEditorFocused() {
    IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    Component focusOwner = focusManager.getFocusOwner();
    Splitter splitter = (Splitter)getComponent();
    return UIUtil.isDescendingFrom(focusOwner, splitter.getFirstComponent());
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return false;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
  }

  private static JComponent wrapWithTitle(String name, final FileEditor beforeEditor) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JBLabel(name, UIUtil.ComponentStyle.SMALL);
    label.setBorder(JBUI.Borders.empty(1, 4, 2, 0));
    panel.add(BorderLayout.NORTH, label);
    panel.add(BorderLayout.CENTER, beforeEditor.getComponent());
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myBeforeEditor.isModified() || myAfterEditor.isModified();
  }

  @Override
  public boolean isValid() {
    return myBeforeEditor.isValid() && myAfterEditor.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
    TextEditorProvider.getInstance().disposeEditor(myBeforeEditor);
    TextEditorProvider.getInstance().disposeEditor(myAfterEditor);
  }
}
