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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 * @author yole
 */
public class TestDataGroupFileEditor extends UserDataHolderBase implements FileEditor {
  private WeakReference<JComponent> myComponent;
  private final TestDataGroupVirtualFile myFile;
  private final FileEditor myBeforeEditor;
  private final FileEditor myAfterEditor;

  public TestDataGroupFileEditor(Project project, TestDataGroupVirtualFile file) {
    myFile = file;
    myBeforeEditor = TextEditorProvider.getInstance().createEditor(project, file.getBeforeFile());
    myAfterEditor = TextEditorProvider.getInstance().createEditor(project, file.getAfterFile());
  }

  @NotNull
  public JComponent getComponent() {
    JComponent result = SoftReference.dereference(myComponent);
    if (result == null) {
      myComponent = new WeakReference<>(result = createComponent());
    }
    return result;
  }

  private JComponent createComponent() {
    Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(wrapWithTitle(myFile.getBeforeFile().getName(), myBeforeEditor));
    splitter.setSecondComponent(wrapWithTitle(myFile.getAfterFile().getName(), myAfterEditor));
    return splitter;
  }

  private static JComponent wrapWithTitle(String name, final FileEditor beforeEditor) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(name);
    label.setBorder(JBUI.Borders.empty(1, 4, 2, 0));
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    panel.add(BorderLayout.NORTH, label);
    panel.add(BorderLayout.CENTER, beforeEditor.getComponent());
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  public String getName() {
    return myFile.getName();
  }

  public void setState(@NotNull FileEditorState state) {
  }

  public boolean isModified() {
    return myBeforeEditor.isModified() || myAfterEditor.isModified();
  }

  public boolean isValid() {
    return myBeforeEditor.isValid() && myAfterEditor.isValid();
  }

  public void selectNotify() {
  }

  public void deselectNotify() {
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  public void dispose() {
    TextEditorProvider.getInstance().disposeEditor(myBeforeEditor);
    TextEditorProvider.getInstance().disposeEditor(myAfterEditor);
  }
}
