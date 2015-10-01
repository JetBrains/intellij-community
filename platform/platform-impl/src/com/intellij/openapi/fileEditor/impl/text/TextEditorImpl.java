/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  @NotNull private final TextEditorComponent myComponent;
  private final TextEditorProvider myProvider;

  TextEditorImpl(@NotNull final Project project, @NotNull final VirtualFile file, final TextEditorProvider provider) {
    myProject = project;
    myProvider = provider;
    myChangeSupport = new PropertyChangeSupport(this);
    myComponent = createEditorComponent(project, file);
  }

  @NotNull
  protected TextEditorComponent createEditorComponent(final Project project, final VirtualFile file) {
    return new TextEditorComponent(project, file, this);
  }

  @Override
  public void dispose(){
    myComponent.dispose();
  }

  @Override
  @NotNull
  public TextEditorComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return getActiveEditor().getContentComponent();
  }

  @Override
  @NotNull
  public Editor getEditor(){
    return getActiveEditor();
  }

  /**
   * @see TextEditorComponent#getEditor()
   */
  @NotNull
  private Editor getActiveEditor() {
    return myComponent.getEditor();
  }

  @Override
  @NotNull
  public String getName() {
    return "Text";
  }

  @Override
  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myProvider.getStateImpl(myProject, getActiveEditor(), level);
  }

  @Override
  public void setState(@NotNull final FileEditorState state) {
    myProvider.setStateImpl(myProject, getActiveEditor(), (TextEditorState)state);
  }

  @Override
  public boolean isModified() {
    return myComponent.isModified();
  }

  @Override
  public boolean isValid() {
    return myComponent.isEditorValid();
  }

  @Override
  public void selectNotify() {
    myComponent.selectNotify();
  }

  @Override
  public void deselectNotify() {
  }

  public void updateModifiedProperty() {
    myComponent.updateModifiedProperty();
  }

  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return new TextEditorLocation(getEditor().getCaretModel().getLogicalPosition(), this);
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    Document document = myComponent.getEditor().getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || !file.isValid()) return null;
    return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, myProject);
  }

  @Override
  public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
    return navigatable instanceof OpenFileDescriptor && (((OpenFileDescriptor)navigatable).getLine() != -1 ||
                                                         ((OpenFileDescriptor)navigatable).getOffset() >= 0);
  }

  @Override
  public void navigateTo(@NotNull final Navigatable navigatable) {
    ((OpenFileDescriptor)navigatable).navigateIn(getEditor());
  }

  @Override
  public String toString() {
    return "Editor: "+getComponent().getFile();
  }
}
