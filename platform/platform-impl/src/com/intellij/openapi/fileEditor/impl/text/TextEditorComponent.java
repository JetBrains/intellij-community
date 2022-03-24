// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.BackgroundableDataProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ui.JBSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class TextEditorComponent extends JBLoadingPanel implements DataProvider, Disposable, BackgroundableDataProvider {
  private static final Logger LOG = Logger.getInstance(TextEditorComponent.class);

  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  private final TextEditorImpl myTextEditor;
  @NotNull private final EditorImpl myEditor;

  /**
   * Whether the editor's document is modified or not
   */
  private boolean myModified;
  /**
   * Whether the editor is valid or not
   */
  private boolean myValid;

  private final EditorHighlighterUpdater myEditorHighlighterUpdater;

  TextEditorComponent(@NotNull final Project project,
                      @NotNull final VirtualFile file,
                      @NotNull final TextEditorImpl textEditor,
                      @NotNull EditorImpl editor) {
    super(new BorderLayout(), textEditor);

    myProject = project;
    myFile = file;
    myTextEditor = textEditor;
    myEditor = editor;
    myEditor.getDocument().addDocumentListener(new MyDocumentListener(), this);
    ((EditorMarkupModel) myEditor.getMarkupModel()).setErrorStripeVisible(true);
    ((EditorGutterComponentEx) myEditor.getGutterComponentEx()).setForceShowRightFreePaintersArea(true);
    myEditor.setFile(myFile);
    myEditor.setContextMenuGroupId(IdeActions.GROUP_EDITOR_POPUP);
    myEditor.setDropHandler(new FileDropHandler(myEditor));
    TextEditorProvider.putTextEditor(myEditor, myTextEditor);    myEditor.getComponent().setFocusable(false);

    add(myEditor.getComponent(), BorderLayout.CENTER);
    myModified = isModifiedImpl();
    myValid = isEditorValidImpl();
    LOG.assertTrue(myValid);

    MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
    myFile.getFileSystem().addVirtualFileListener(myVirtualFileListener);
    Disposer.register(this, () -> myFile.getFileSystem().removeVirtualFileListener(myVirtualFileListener));

    myEditorHighlighterUpdater = new EditorHighlighterUpdater(myProject, this, myEditor, myFile);

    project.getMessageBus().connect(this).subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
  }

  private volatile boolean myDisposed;
  /**
   * Disposes all resources allocated be the TextEditorComponent. It disposes all created
   * editors, unregisters listeners. The behaviour of the splitter after disposing is
   * unpredictable.
   */
  @Override
  public void dispose(){
    disposeEditor();
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public void loadingFinished() {
    if (isLoading()) {
      stopLoading();
    }

    getContentPanel().setVisible(true);
  }

  private static void assertThread(){
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  /**
   * @return most recently used editor. This method never returns {@code null}.
   */
  @NotNull
  Editor getEditor(){
    return myEditor;
  }

  private void disposeEditor(){
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  /**
   * @return whether the editor's document is modified or not
   */
  boolean isModified(){
    assertThread();
    return myModified;
  }

  /**
   * Just calculates "modified" property
   */
  private boolean isModifiedImpl(){
    return FileDocumentManager.getInstance().isFileModified(myFile);
  }

  /**
   * Updates "modified" property and fires event if necessary
   */
  void updateModifiedProperty(){
    Boolean oldModified= myModified;
    myModified = isModifiedImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_MODIFIED, oldModified, myModified);
  }

  /**
   * Name {@code isValid} is in use in {@code java.awt.Component}
   * so we change the name of method to {@code isEditorValid}
   *
   * @return whether the editor is valid or not
   */
  boolean isEditorValid(){
    return myValid && !myEditor.isDisposed();
  }

  /**
   * Just calculates
   */
  private boolean isEditorValidImpl(){
    return FileDocumentManager.getInstance().getDocument(myFile) != null;
  }

  private void updateValidProperty(){
    Boolean oldValid = myValid;
    myValid = isEditorValidImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_VALID, oldValid, myValid);
  }

  @Nullable
  @Override
  public DataProvider createBackgroundDataProvider() {
    if (myEditor.isDisposed()) return null;

    // There's no FileEditorManager for default project (which is used in diff command-line application)
    FileEditorManager fileEditorManager = !myProject.isDisposed() && !myProject.isDefault() ? FileEditorManager.getInstance(myProject) : null;
    Caret currentCaret = myEditor.getCaretModel().getCurrentCaret();
    return dataId -> {
      if (fileEditorManager != null) {
        Object o = fileEditorManager.getData(dataId, myEditor, currentCaret);
        if (o != null) return o;
      }

      if (CommonDataKeys.EDITOR.is(dataId)) {
        return myEditor;
      }
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return myFile.isValid() ? myFile : null;  // fix for SCR 40329
      }
      return null;
    };
  }

  /**
   * Updates "modified" property
   */
  private final class MyDocumentListener implements DocumentListener {
    /**
     * We can reuse this runnable to decrease number of allocated object.
     */
    private final Runnable myUpdateRunnable;
    private boolean myUpdateScheduled;

    MyDocumentListener() {
      myUpdateRunnable = () -> {
        myUpdateScheduled = false;
        updateModifiedProperty();
      };
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (!myUpdateScheduled) {
        // document's timestamp is changed later on undo or PSI changes
        ApplicationManager.getApplication().invokeLater(myUpdateRunnable);
        myUpdateScheduled = true;
      }
    }
  }

  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull final FileTypeEvent event) {
      assertThread();
      // File can be invalid after file type changing. The editor should be removed
      // by the FileEditorManager if it's invalid.
      updateValidProperty();
    }
  }

  /**
   * Updates "valid" property and highlighters (if necessary)
   */
  private final class MyVirtualFileListener implements VirtualFileListener {
    @Override
    public void propertyChanged(@NotNull final VirtualFilePropertyEvent e) {
      if(VirtualFile.PROP_NAME.equals(e.getPropertyName())){
        // File can be invalidated after file changes name (extension also
        // can changes). The editor should be removed if it's invalid.
        updateValidProperty();
        if (Comparing.equal(e.getFile(), myFile) &&
            (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(e.getRequestor()) ||
             !Comparing.equal(e.getOldValue(), e.getNewValue()))) {
          myEditorHighlighterUpdater.updateHighlighters();
        }
      }
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event){
      if (event.isFromSave()){ // commit
        assertThread();
        VirtualFile file = event.getFile();
        LOG.assertTrue(file.isValid());
        if(myFile.equals(file)){
          updateModifiedProperty();
        }
      }
    }
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public Color getBackground() {
    //noinspection ConstantConditions
    return myEditor == null ? super.getBackground() : myEditor.getContentComponent().getBackground();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics g) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
  }
}
