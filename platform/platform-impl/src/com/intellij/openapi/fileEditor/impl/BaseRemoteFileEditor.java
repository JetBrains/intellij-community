package com.intellij.openapi.fileEditor.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.Navigatable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class BaseRemoteFileEditor implements TextEditor, PropertyChangeListener {
  protected Editor myMockTextEditor;
  protected volatile Navigatable myPendingNavigatable;

  protected final Project myProject;
  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final EventDispatcher<PropertyChangeListener> myDispatcher = EventDispatcher.create(PropertyChangeListener.class);

  protected BaseRemoteFileEditor(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder() {
    TextEditor textEditor = getTextEditor();
    return textEditor == null ? null : textEditor.getStructureViewBuilder();
  }

  @Override
  @NotNull
  public Editor getEditor() {
    TextEditor fileEditor = getTextEditor();
    if (fileEditor != null) {
      return fileEditor.getEditor();
    }
    else if (myMockTextEditor == null) {
      myMockTextEditor = EditorFactory.getInstance().createViewer(new DocumentImpl(""), myProject);
    }
    return myMockTextEditor;
  }

  @Nullable
  protected abstract TextEditor getTextEditor();

  @Override
  public FileEditorLocation getCurrentLocation() {
    TextEditor textEditor = getTextEditor();
    return textEditor == null ? null : textEditor.getCurrentLocation();
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    TextEditor textEditor = getTextEditor();
    return textEditor == null ? null : textEditor.getBackgroundHighlighter();
  }

  @Override
  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    TextEditor textEditor = getTextEditor();
    return textEditor == null ? new TextEditorState() : textEditor.getState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    TextEditor textEditor = getTextEditor();
    if (textEditor != null && state instanceof TextEditorState) {
      textEditor.setState(state);
    }
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    TextEditor textEditor = getTextEditor();
    return textEditor == null ? myUserDataHolder.getUserData(key) : textEditor.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    TextEditor textEditor = getTextEditor();
    if (textEditor == null) {
      myUserDataHolder.putUserData(key, value);
    }
    else {
      textEditor.putUserData(key, value);
    }
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void dispose() {
    if (myMockTextEditor != null) {
      EditorFactory.getInstance().releaseEditor(myMockTextEditor);
    }
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    myDispatcher.getMulticaster().propertyChange(evt);
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    TextEditor editor = getTextEditor();
    return editor == null ? isValid() : editor.canNavigateTo(navigatable);
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    TextEditor editor = getTextEditor();
    if (editor != null) {
      editor.navigateTo(navigatable);
    }
    else if (isValid()) {
      myPendingNavigatable = navigatable;
    }
  }

  protected void checkPendingNavigable() {
    Navigatable navigatable = myPendingNavigatable;
    if (navigatable != null) {
      myPendingNavigatable = null;
      TextEditor editor = getTextEditor();
      assert editor != null;
      editor.navigateTo(navigatable);
    }
  }
}