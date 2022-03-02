// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Vladimir Kondratyev
 */
public class TextEditorImpl extends UserDataHolderBase implements TextEditor {
  private static final Logger LOG = Logger.getInstance(TextEditorImpl.class);

  private static final Key<TransientEditorState> TRANSIENT_EDITOR_STATE_KEY = Key.create("transientState");

  protected final Project myProject;
  private final PropertyChangeSupport myChangeSupport;
  @NotNull private final TextEditorComponent myComponent;
  @NotNull protected final VirtualFile myFile;
  private final AsyncEditorLoader myAsyncLoader;

  TextEditorImpl(@NotNull Project project, @NotNull VirtualFile file, @NotNull TextEditorProvider provider) {
    this(project, file, provider, createEditor(project, file));
  }

  TextEditorImpl(@NotNull Project project, @NotNull VirtualFile file, @NotNull TextEditorProvider provider, @NotNull EditorImpl editor) {
    myProject = project;
    myFile = file;
    myChangeSupport = new PropertyChangeSupport(this);
    myComponent = createEditorComponent(project, file, editor);
    applyTextEditorCustomizers();

    TransientEditorState state = myFile.getUserData(TRANSIENT_EDITOR_STATE_KEY);
    if (state != null) {
      state.applyTo(getEditor());
      myFile.putUserData(TRANSIENT_EDITOR_STATE_KEY, null);
    }

    Disposer.register(this, myComponent);
    myAsyncLoader = new AsyncEditorLoader(this, myComponent, provider);
    myAsyncLoader.start();
  }

  /**
   * @return a continuation to be called in EDT
   */
  @NotNull
  protected Runnable loadEditorInBackground() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myFile, scheme, myProject);
    EditorEx editor = (EditorEx)getEditor();
    highlighter.setText(editor.getDocument().getImmutableCharSequence());
    return () -> {
      editor.getSettings().setLanguageSupplier(() -> getDocumentLanguage(editor));
      editor.setHighlighter(highlighter);
    };
  }

  @Nullable
  public static Language getDocumentLanguage(@NotNull Editor editor) {
    Project project = editor.getProject();
    LOG.assertTrue(project != null);
    if (!project.isDisposed()) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      PsiFile file = documentManager.getPsiFile(editor.getDocument());
      if (file != null) return file.getLanguage();
    }
    else {
      LOG.warn("Attempting to get a language for document on a disposed project: " + project.getName());
    }
    return null;
  }

  @NotNull
  protected TextEditorComponent createEditorComponent(@NotNull Project project, @NotNull VirtualFile file, @NotNull EditorImpl editor) {
    return new TextEditorComponent(project, file, this, editor);
  }

  @Override
  public void dispose(){
    if (Boolean.TRUE.equals(myFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) {
      myFile.putUserData(TRANSIENT_EDITOR_STATE_KEY, TransientEditorState.forEditor(getEditor()));
    }
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public TextEditorComponent getComponent() {
    return myComponent;
  }

  @Override
  @NotNull
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
    return IdeBundle.message("tab.title.text");
  }

  @Override
  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myAsyncLoader.getEditorState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    setState(state, false);
  }

  @Override
  public void setState(@NotNull FileEditorState state, boolean exactState) {
    if (state instanceof TextEditorState) {
      myAsyncLoader.setEditorState((TextEditorState)state, exactState);
    }
  }

  @Override
  public boolean isModified() {
    return myComponent.isModified();
  }

  @Override
  public boolean isValid() {
    return myComponent.isEditorValid();
  }

  public void updateModifiedProperty() {
    myComponent.updateModifiedProperty();
  }

  void firePropertyChange(@NotNull String propertyName, Object oldValue, Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
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
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return navigatable instanceof OpenFileDescriptor &&
           (((OpenFileDescriptor)navigatable).getLine() >= 0 || ((OpenFileDescriptor)navigatable).getOffset() >= 0);
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    ((OpenFileDescriptor)navigatable).navigateIn(getEditor());
  }

  @Override
  @NonNls
  public String toString() {
    return "Editor: "+myComponent.getFile();
  }

  private void applyTextEditorCustomizers() {
    for (TextEditorCustomizer customizer : TextEditorCustomizer.EP.getExtensionList()) {
      customizer.customize(this);
    }
  }

  private static class TransientEditorState {
    private boolean softWrapsEnabled;

    private static @NotNull TransientEditorState forEditor(@NotNull Editor editor) {
      TransientEditorState state = new TransientEditorState();
      state.softWrapsEnabled = editor.getSettings().isUseSoftWraps();
      return state;
    }

    private void applyTo(@NotNull Editor editor) {
      editor.getSettings().setUseSoftWraps(softWrapsEnabled);
    }
  }

  private static EditorImpl createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    LOG.assertTrue(document != null);
    return (EditorImpl)EditorFactory.getInstance().createEditor(document, project, EditorKind.MAIN_EDITOR);
  }
}
