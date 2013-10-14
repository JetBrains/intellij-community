/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class TextEditorComponent extends JBLoadingPanel implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.TextEditorComponent");

  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  private final TextEditorImpl myTextEditor;
  /**
   * Document to be edited
   */
  private final Document myDocument;

  private final MyEditorMouseListener myEditorMouseListener;
  private final MyDocumentListener myDocumentListener;
  private final MyVirtualFileListener myVirtualFileListener;
  @NotNull private final Editor myEditor;

  /**
   * Whether the editor's document is modified or not
   */
  private boolean myModified;
  /**
   * Whether the editor is valid or not
   */
  private boolean myValid;
  private final MessageBusConnection myConnection;

  TextEditorComponent(@NotNull final Project project, @NotNull final VirtualFile file, @NotNull final TextEditorImpl textEditor) {
    super(new BorderLayout(), textEditor);

    myProject = project;
    myFile = file;
    myTextEditor = textEditor;

    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    LOG.assertTrue(myDocument!=null);
    myDocumentListener = new MyDocumentListener();
    myDocument.addDocumentListener(myDocumentListener);

    myEditorMouseListener = new MyEditorMouseListener();

    myConnection = project.getMessageBus().connect();
    myConnection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());

    myVirtualFileListener = new MyVirtualFileListener();
    myFile.getFileSystem().addVirtualFileListener(myVirtualFileListener);
    myEditor = createEditor();
    add(myEditor.getComponent(), BorderLayout.CENTER);
    myModified = isModifiedImpl();
    myValid = isEditorValidImpl();
    LOG.assertTrue(myValid);
  }

  /**
   * Disposes all resources allocated be the TextEditorComponent. It disposes all created
   * editors, unregisters listeners. The behaviour of the splitter after disposing is
   * unpredictable.
   */
  void dispose(){
    myDocument.removeDocumentListener(myDocumentListener);
    EditorHistoryManager.getInstance(myProject).updateHistoryEntry(myFile, false);
    disposeEditor(myEditor);
    myConnection.disconnect();

    myFile.getFileSystem().removeVirtualFileListener(myVirtualFileListener);
    //myFocusWatcher.deinstall(this);
    //removePropertyChangeListener(mySplitterPropertyChangeListener);

    //super.dispose();
  }

  /**
   * Should be invoked when the corresponding <code>TextEditorImpl</code>
   * is selected. Updates the status bar.
   */
  void selectNotify(){
    updateStatusBar();
  }

  private static void assertThread(){
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  /**
   * @return most recently used editor. This method never returns <code>null</code>.
   */
  @NotNull
  Editor getEditor(){
    return myEditor;
  }

  /**
   * @return created editor. This editor should be released by {@link #disposeEditor(Editor) }
   * method.
   */
  @NotNull
  private Editor createEditor(){
    Editor editor = EditorFactory.getInstance().createEditor(myDocument, myProject);
    ((EditorMarkupModel) editor.getMarkupModel()).setErrorStripeVisible(true);
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myFile, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
    ((EditorEx) editor).setHighlighter(highlighter);
    ((EditorEx) editor).setFile(myFile);

    editor.addEditorMouseListener(myEditorMouseListener);

    ((EditorImpl) editor).setDropHandler(new FileDropHandler(editor));

    TextEditorProvider.putTextEditor(editor, myTextEditor);
    return editor;
  }

  /**
   * Disposes resources allocated by the specified editor view and registers all
   * it's listeners
   */
  private void disposeEditor(@NotNull Editor editor){
    EditorFactory.getInstance().releaseEditor(editor);
    editor.removeEditorMouseListener(myEditorMouseListener);
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
    Boolean oldModified=Boolean.valueOf(myModified);
    myModified = isModifiedImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_MODIFIED, oldModified, Boolean.valueOf(myModified));
  }

  /**
   * Name <code>isValid</code> is in use in <code>java.awt.Component</code>
   * so we change the name of method to <code>isEditorValid</code>
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
    Boolean oldValid = Boolean.valueOf(myValid);
    myValid = isEditorValidImpl();
    myTextEditor.firePropertyChange(FileEditor.PROP_VALID, oldValid, Boolean.valueOf(myValid));
  }

  /**
   * Updates editors' highlighters. This should be done when the opened file
   * changes its file type.
   */
  private void updateHighlighters(){
    if (!myProject.isDisposed()) {
      final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFile);
      ((EditorEx)myEditor).setHighlighter(highlighter);
    }
  }

  /**
   * Updates frame's status bar: insert/overwrite mode, caret position
   */
  private void updateStatusBar(){
    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar == null) return;
    statusBar.updateWidgets(); // TODO: do we need this?!
  }

  @Nullable
  private Editor validateCurrentEditor() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner instanceof JComponent) {
      final JComponent jComponent = (JComponent)focusOwner;
      if (jComponent.getClientProperty("AuxEditorComponent") != null) return null; // Hack for EditorSearchComponent
    }

    return myEditor;
  }

  @Override
  public Object getData(final String dataId) {
    final Editor e = validateCurrentEditor();
    if (e == null) return null;

    if (!myProject.isDisposed()) {
      final Object o = ((FileEditorManagerImpl)FileEditorManager.getInstance(myProject)).getData(dataId, e, myFile);
      if (o != null) return o;
    }

    if (CommonDataKeys.EDITOR.is(dataId)) {
      return e;
    }
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return myFile.isValid()? myFile : null;  // fix for SCR 40329
    }
    return null;
  }

  /**
   * Shows popup menu
   */
  private static final class MyEditorMouseListener extends EditorPopupHandler {
    @Override
    public void invokePopup(final EditorMouseEvent event) {
      if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_POPUP);
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
        MouseEvent e = event.getMouseEvent();
        final Component c = e.getComponent();
        if (c != null && c.isShowing()) {
          popupMenu.getComponent().show(c, e.getX(), e.getY());
        }
        e.consume();
      }
    }
  }


  /**
   * Updates "modified" property
   */
  private final class MyDocumentListener extends DocumentAdapter {
    /**
     * We can reuse this runnable to decrease number of allocated object.
     */
    private final Runnable myUpdateRunnable;

    public MyDocumentListener() {
      myUpdateRunnable = new Runnable() {
        @Override
        public void run() {
          updateModifiedProperty();
        }
      };
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      // document's timestamp is changed later on undo or PSI changes
      ApplicationManager.getApplication().invokeLater(myUpdateRunnable);
    }
  }

  /**
   * Listen changes of file types. When type of the file changes we need
   * to also change highlighter.
   */
  private final class MyFileTypeListener extends FileTypeListener.Adapter {
    @Override
    public void fileTypesChanged(final FileTypeEvent event) {
      assertThread();
      // File can be invalid after file type changing. The editor should be removed
      // by the FileEditorManager if it's invalid.
      updateValidProperty();
      updateHighlighters();
    }
  }

  /**
   * Updates "valid" property and highlighters (if necessary)
   */
  private final class MyVirtualFileListener extends VirtualFileAdapter{
    @Override
    public void propertyChanged(final VirtualFilePropertyEvent e) {
      if(VirtualFile.PROP_NAME.equals(e.getPropertyName())){
        // File can be invalidated after file changes name (extension also
        // can changes). The editor should be removed if it's invalid.
        updateValidProperty();
        updateHighlighters();
      }
    }

    @Override
    public void contentsChanged(VirtualFileEvent event){
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
}
