/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Vladimir Kondratyev
 */
public class TextEditorImpl extends UserDataHolderBase implements TextEditor {
  protected final Project myProject;
  private final PropertyChangeSupport myChangeSupport;
  private final TextEditorComponent myComponent;
  private final TextEditorProvider myProvider;

  TextEditorImpl(@NotNull final Project project, @NotNull final VirtualFile file, final TextEditorProvider provider) {
    myProject = project;
    myProvider = provider;
    myChangeSupport = new PropertyChangeSupport(this);
    myComponent = createEditorComponent(project, file);
  }

  protected TextEditorComponent createEditorComponent(final Project project, final VirtualFile file) {
    return new TextEditorComponent(project, file, this);
  }

  public void initFolding() {}

  public void dispose(){
    myComponent.dispose();
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getPreferredFocusedComponent(){
    return getActiveEditor().getContentComponent();
  }

  @NotNull
  public Editor getEditor(){
    return getActiveEditor();
  }

  /**
   * @see TextEditorComponent#getEditor()
   */
  private Editor getActiveEditor() {
    return myComponent.getEditor();
  }

  @NotNull
  public String getName() {
    return "Text";
  }

  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myProvider.getStateImpl(myProject, getActiveEditor(), level);
  }

  public void setState(@NotNull final FileEditorState state) {
    myProvider.setStateImpl(myProject, getActiveEditor(), (TextEditorState)state);
  }

  public boolean isModified() {
    return myComponent.isModified();
  }

  public boolean isValid() {
    return myComponent.isEditorValid();
  }

  public void selectNotify() {
    myComponent.selectNotify();
  }

  public void deselectNotify() {
    myComponent.deselectNotify();
  }

  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return new TextEditorLocation(getEditor().getCaretModel().getLogicalPosition(), this);
  }

  public StructureViewBuilder getStructureViewBuilder() {
    Document document = myComponent.getEditor().getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || !file.isValid()) return null;
    return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, myProject);
  }

  public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
    return navigatable instanceof OpenFileDescriptor && (((OpenFileDescriptor)navigatable).getOffset() >= 0 ||
                                                         ((OpenFileDescriptor)navigatable).getLine() != -1 &&
                                                         ((OpenFileDescriptor)navigatable).getColumn() != -1);
  }

  public void navigateTo(@NotNull final Navigatable navigatable) {
    OpenFileDescriptor d = (OpenFileDescriptor)navigatable;
    d.navigateIn(getEditor());
  }
}
