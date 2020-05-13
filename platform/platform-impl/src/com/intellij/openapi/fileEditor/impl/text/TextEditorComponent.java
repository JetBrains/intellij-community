// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.BackgroundableDataProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  /**
   * Document to be edited
   */
  private final Document myDocument;

  @NotNull private final Editor myEditor;

  /**
   * Whether the editor's document is modified or not
   */
  private boolean myModified;
  /**
   * Whether the editor is valid or not
   */
  private boolean myValid;

  private final EditorHighlighterUpdater myEditorHighlighterUpdater;

  TextEditorComponent(@NotNull final Project project, @NotNull final VirtualFile file, @NotNull final TextEditorImpl textEditor) {
    super(new BorderLayout(), textEditor);

    myProject = project;
    myFile = file;
    myTextEditor = textEditor;

    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    LOG.assertTrue(myDocument!=null);
    myDocument.addDocumentListener(new MyDocumentListener(), this);

    myEditor = createEditor();
    myEditor.getComponent().setFocusable(false);
    add(myEditor.getComponent(), BorderLayout.CENTER);
    myModified = isModifiedImpl();
    myValid = isEditorValidImpl();
    LOG.assertTrue(myValid);

    MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
    myFile.getFileSystem().addVirtualFileListener(myVirtualFileListener);
    Disposer.register(this, ()-> myFile.getFileSystem().removeVirtualFileListener(myVirtualFileListener));

    myEditorHighlighterUpdater = new EditorHighlighterUpdater(myProject, this, (EditorEx)myEditor, myFile);

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

  @NotNull
  private Editor createEditor(){
    Editor editor = EditorFactory.getInstance().createEditor(myDocument, myProject, EditorKind.MAIN_EDITOR);
    ((EditorMarkupModel) editor.getMarkupModel()).setErrorStripeVisible(true);
    ((EditorEx) editor).getGutterComponentEx().setForceShowRightFreePaintersArea(true);

    ((EditorEx) editor).setFile(myFile);

    ((EditorEx)editor).setContextMenuGroupId(IdeActions.GROUP_EDITOR_POPUP);

    ((EditorImpl) editor).setDropHandler(new FileDropHandler(editor));

    TextEditorProvider.putTextEditor(editor, myTextEditor);
    return editor;
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
  private Editor validateCurrentEditor() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner instanceof JComponent) {
      final JComponent jComponent = (JComponent)focusOwner;
      if (jComponent.getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null) return null;
    }

    return myEditor;
  }

  @Nullable
  @Override
  public DataProvider createBackgroundDataProvider() {
    final Editor e = validateCurrentEditor();
    if (e == null || e.isDisposed()) return null;

    // There's no FileEditorManager for default project (which is used in diff command-line application)
    FileEditorManager fileEditorManager = !myProject.isDisposed() && !myProject.isDefault() ? FileEditorManager.getInstance(myProject) : null;
    Caret currentCaret = e.getCaretModel().getCurrentCaret();
    return dataId -> {
      if (fileEditorManager != null) {
        Object o = fileEditorManager.getData(dataId, e, currentCaret);
        if (o != null) return o;
      }

      if (CommonDataKeys.EDITOR.is(dataId)) {
        return e;
      }
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return myFile.isValid()? myFile : null;  // fix for SCR 40329
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
