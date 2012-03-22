/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.AppTopics;
import com.intellij.CommonBundle;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

public class FileDocumentManagerImpl extends FileDocumentManager implements ApplicationComponent, VirtualFileListener, SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl");

  private static final Key<String> LINE_SEPARATOR_KEY = Key.create("LINE_SEPARATOR_KEY");
  public static final Key<Reference<Document>> DOCUMENT_KEY = Key.create("DOCUMENT_KEY");
  private static final Key<VirtualFile> FILE_KEY = Key.create("FILE_KEY");

  private final Set<Document> myUnsavedDocuments = new ConcurrentHashSet<Document>();

  private final MessageBus myBus;

  private static final Object lock = new Object();
  private final FileDocumentManagerListener myMultiCaster;
  private final TrailingSpacesStripper myTrailingSpacesStripper = new TrailingSpacesStripper();

  public FileDocumentManagerImpl(VirtualFileManager virtualFileManager) {
    virtualFileManager.addVirtualFileListener(this);

    myBus = ApplicationManager.getApplication().getMessageBus();
    InvocationHandler handler = new InvocationHandler() {
      @Nullable
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        multiCast(method, args);
        return null;
      }
    };

    final ClassLoader loader = FileDocumentManagerListener.class.getClassLoader();
    myMultiCaster = (FileDocumentManagerListener)Proxy.newProxyInstance(loader, new Class[]{FileDocumentManagerListener.class}, handler);
  }

  private void multiCast(Method method, Object[] args) {
    try {
      method.invoke(myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC), args);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    // Allows pre-save document modification
    for (FileDocumentManagerListener listener : getListeners()) {
      try {
        method.invoke(listener, args);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    // stripping trailing spaces
    try {
      method.invoke(myTrailingSpacesStripper, args);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "FileDocumentManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @Nullable
  public Document getDocument(@NotNull final VirtualFile file) {
    DocumentEx document = (DocumentEx)getCachedDocument(file);
    if (document == null) {
      if (file.isDirectory() || isBinaryWithoutDecompiler(file)) return null;
      final CharSequence text = LoadTextUtil.loadText(file);

      synchronized (lock) {
        document = (DocumentEx)getCachedDocument(file);
        if (document != null) return document; // Double checking

        document = (DocumentEx)createDocument(text);
        document.setModificationStamp(file.getModificationStamp());
        final FileType fileType = file.getFileType();
        document.setReadOnly(!file.isWritable() || fileType.isBinary());
        file.putUserData(DOCUMENT_KEY, new WeakReference<Document>(document));
        document.putUserData(FILE_KEY, file);

        if (!(file instanceof LightVirtualFile || file.getFileSystem() instanceof DummyFileSystem)) {
          document.addDocumentListener(
            new DocumentAdapter() {
              @Override
              public void documentChanged(DocumentEvent e) {
                final Document document = e.getDocument();
                myUnsavedDocuments.add(document);
                final Runnable currentCommand = CommandProcessor.getInstance().getCurrentCommand();
                Project project = currentCommand == null ? null : CommandProcessor.getInstance().getCurrentCommandProject();
                String lineSeparator = CodeStyleFacade.getInstance(project).getLineSeparator();
                document.putUserData(LINE_SEPARATOR_KEY, lineSeparator);

                // avoid documents piling up during batch processing
                if (areTooManyDocumentsInTheQueue(myUnsavedDocuments)) {
                  saveAllDocumentsLater();
                }
              }
            }
          );
        }
      }

      myMultiCaster.fileContentLoaded(file, document);
    }

    return document;
  }

  public static boolean areTooManyDocumentsInTheQueue(Collection<Document> documents) {
    if (documents.size() > 100) return true;
    int totalSize = 0;
    for (Document document : documents) {
      totalSize += document.getTextLength();
      if (totalSize > 10 * FileUtil.MEGABYTE) return true;
    }
    return false;
  }

  private static Document createDocument(final CharSequence text) {
    return EditorFactory.getInstance().createDocument(text);
  }

  @Override
  @Nullable
  public Document getCachedDocument(@NotNull VirtualFile file) {
    Reference<Document> reference = file.getUserData(DOCUMENT_KEY);
    Document document = reference != null ? reference.get() : null;

    if (document != null && isBinaryWithoutDecompiler(file)) {
      file.putUserData(DOCUMENT_KEY, null);
      document.putUserData(FILE_KEY, null);
      return null;
    }

    return document;
  }

  public static void registerDocument(@NotNull final Document document, @NotNull VirtualFile virtualFile) {
    synchronized (lock) {
      virtualFile.putUserData(DOCUMENT_KEY, new SoftReference<Document>(document) {
        @Override
        public Document get() {
          return document;
        }
      });
      document.putUserData(FILE_KEY, virtualFile);
    }
  }

  @Override
  @Nullable
  public VirtualFile getFile(@NotNull Document document) {
    return document.getUserData(FILE_KEY);
  }

  @TestOnly
  public void dropAllUnsavedDocuments() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("This method is only for test mode!");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!myUnsavedDocuments.isEmpty()) {
      myUnsavedDocuments.clear();
      fireUnsavedDocumentsDropped();
    }
  }

  private void saveAllDocumentsLater() {
    // later because some document might have been blocked by PSI right now
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) {
          return;
        }
        final Document[] unsavedDocuments = getUnsavedDocuments();
        for (Document document : unsavedDocuments) {
          VirtualFile file = getFile(document);
          if (file == null) continue;
          Project project = ProjectUtil.guessProjectForFile(file);
          if (project == null) continue;
          if (PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(document)) continue;

          saveDocument(document);
        }
      }
    });
  }

  @Override
  public void saveAllDocuments() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myMultiCaster.beforeAllDocumentsSaving();
    if (myUnsavedDocuments.isEmpty()) return;

    final Map<Document, IOException> failedToSave = new HashMap<Document, IOException>();
    while (true) {
      int count = 0;

      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        for (Document document : myUnsavedDocuments) {
          if (failedToSave.containsKey(document)) continue;
          try {
            doSaveDocument(document);
          }
          catch (IOException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            failedToSave.put(document, e);
          }
          count++;
        }
      }
      finally {
        token.finish();
      }

      if (count == 0) break;
    }

    if (!failedToSave.isEmpty()) {
      handleErrorsOnSave(failedToSave);
    }
  }

  @Override
  public void saveDocument(@NotNull final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myUnsavedDocuments.contains(document)) return;

    ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(document, null) {
      @Override
      public void run() {
        try {
          doSaveDocument(document);
        }
        catch (IOException e) {
          handleErrorsOnSave(Collections.singletonMap(document, e));
        }
      }
    });
  }

  @Override
  public void saveDocumentAsIs(@NotNull Document document) {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();

    String trailer = editorSettings.getStripTrailingSpaces();
    boolean ensureEOLonEOF = editorSettings.isEnsureNewLineAtEOF();
    editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    editorSettings.setEnsureNewLineAtEOF(false);
    try {
      saveDocument(document);
    }
    finally {
      editorSettings.setStripTrailingSpaces(trailer);
      editorSettings.setEnsureNewLineAtEOF(ensureEOLonEOF);
    }
  }

  private void doSaveDocument(@NotNull final Document document) throws IOException {
    VirtualFile file = getFile(document);

    if (file == null || !file.isValid() || file instanceof LightVirtualFile || !isFileModified(file)) {
      myUnsavedDocuments.remove(document);
      fireUnsavedDocumentsDropped();
      LOG.assertTrue(!myUnsavedDocuments.contains(document));
      return;
    }

    if (needsRefresh(file)) {
      file.refresh(false, false);
      if (!myUnsavedDocuments.contains(document)) return;
      if (!file.isValid()) return;
    }

    for (FileDocumentSynchronizationVetoer vetoer : Extensions.getExtensions(FileDocumentSynchronizationVetoer.EP_NAME)) {
      if (!vetoer.maySaveDocument(document)) {
        return;
      }
    }

    myMultiCaster.beforeDocumentSaving(document);

    LOG.assertTrue(file.isValid());

    String text = document.getText();
    String lineSeparator = getLineSeparator(document, file);
    if (!lineSeparator.equals("\n")) {
      text = StringUtil.convertLineSeparators(text, lineSeparator);
    }

    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    Writer writer = LoadTextUtil.getWriter(project, file, this, text, document.getModificationStamp());
    try {
      writer.write(text);
    }
    finally {
      writer.close();
    }

    myUnsavedDocuments.remove(document);
    LOG.assertTrue(!myUnsavedDocuments.contains(document));
    myTrailingSpacesStripper.clearLineModificationFlags(document);
  }

  private static boolean needsRefresh(final VirtualFile file) {
    final VirtualFileSystem fs = file.getFileSystem();
    return fs instanceof NewVirtualFileSystem && file.getTimeStamp() != ((NewVirtualFileSystem)fs).getTimeStamp(file);
  }

  private static String getLineSeparator(Document document, VirtualFile file) {
    String lineSeparator = LoadTextUtil.getDetectedLineSeparator(file);
    if (lineSeparator == null) {
      lineSeparator = document.getUserData(LINE_SEPARATOR_KEY);
      assert lineSeparator != null : document;
    }
    return lineSeparator;
  }

  @Override
  @NotNull
  public String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project) {
    String lineSeparator = file != null ? LoadTextUtil.getDetectedLineSeparator(file) : null;
    if (lineSeparator == null) {
      CodeStyleFacade settingsManager = project == null
                                        ? CodeStyleFacade.getInstance()
                                        : CodeStyleFacade.getInstance(project);
      return settingsManager.getLineSeparator();
    }
    else {
      return lineSeparator;
    }
  }

  @Override
  public boolean requestWriting(@NotNull Document document, Project project) {
    final VirtualFile file = getInstance().getFile(document);
    if (project != null && file != null && file.isValid()) {
      return ReadonlyStatusHandler.ensureFilesWritable(project, file);
    }
    if (document.isWritable()) {
      return true;
    }
    document.fireReadOnlyModificationAttempt();
    return false;
  }

  @Override
  public void reloadFiles(final VirtualFile... files) {
    for (VirtualFile file : files) {
      if (file.exists()) {
        final Document doc = getCachedDocument(file);
        if (doc != null) {
          reloadFromDisk(doc);
        }
      }
    }
  }

  @Override
  @NotNull
  public Document[] getUnsavedDocuments() {
    if (myUnsavedDocuments.isEmpty()) {
      return Document.EMPTY_ARRAY;
    }

    List<Document> list = new ArrayList<Document>(myUnsavedDocuments);
    return list.toArray(new Document[list.size()]);
  }

  @Override
  public boolean isDocumentUnsaved(@NotNull Document document) {
    return myUnsavedDocuments.contains(document);
  }

  @Override
  public boolean isFileModified(@NotNull VirtualFile file) {
    final Document doc = getCachedDocument(file);
    return doc != null && isDocumentUnsaved(doc) && doc.getModificationStamp() != file.getModificationStamp();
  }

  @Override
  public void propertyChanged(final VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      final VirtualFile file = event.getFile();
      final Document document = getCachedDocument(file);
      if (document == null) return;

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          @Override
          public void run() {
            document.setReadOnly(!event.getFile().isWritable());
          }
        }
      );
      //myUnsavedDocuments.remove(document); //?
    }
  }

  private static boolean isBinaryWithDecompiler(VirtualFile file) {
    final FileType ft = file.getFileType();
    return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) != null;
  }

  private static boolean isBinaryWithoutDecompiler(VirtualFile file) {
    final FileType ft = file.getFileType();
    return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) == null;
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    if (event.isFromSave()) return;
    final VirtualFile file = event.getFile();
    final Document document = getCachedDocument(file);
    if (document == null) {
      fireFileWithNoDocumentChanged(file);
      return;
    }

    if (isBinaryWithDecompiler(file)) {
      fireFileWithNoDocumentChanged(file); // This will generate PSI event at FileManagerImpl
    }

    long documentStamp = document.getModificationStamp();
    long oldFileStamp = event.getOldModificationStamp();
    if (documentStamp != oldFileStamp) {
      LOG.info("reload from disk?");
      LOG.info("  documentStamp:" + documentStamp);
      LOG.info("  oldFileStamp:" + oldFileStamp);

      Runnable askReloadRunnable = new Runnable() {
        @Override
        public void run() {
          if (!file.isValid()) return;
          if (askReloadFromDisk(file, document)) {
            reloadFromDisk(document);
          }
        }
      };

      askReloadRunnable.run();
    }
    else {
      reloadFromDisk(document);
    }
  }

  private void fireFileWithNoDocumentChanged(@NotNull VirtualFile file) {
    myMultiCaster.fileWithNoDocumentChanged(file);
  }

  @Override
  public void reloadFromDisk(@NotNull final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final VirtualFile file = getFile(document);
    assert file != null;

    if (!fireBeforeFileContentReload(file, document)) {
      return;
    }

    final Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(
          new ExternalChangeAction.ExternalDocumentChange(document, project) {
            @Override
            public void run() {
              boolean wasWritable = document.isWritable();
              DocumentEx documentEx = (DocumentEx)document;
              documentEx.setReadOnly(false);
              documentEx.replaceText(LoadTextUtil.loadText(file), file.getModificationStamp());
              documentEx.setReadOnly(!wasWritable);
            }
          }
        );
      }
    }, UIBundle.message("file.cache.conflict.action"), null);

    myUnsavedDocuments.remove(document);

    myMultiCaster.fileContentReloaded(file, document);
  }

  protected boolean askReloadFromDisk(final VirtualFile file, final Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!isDocumentUnsaved(document)) return true;

    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    final DialogBuilder builder = new DialogBuilder((Project)null);
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.addAction(new AbstractAction(UIBundle.message("file.cache.conflict.show.difference.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        String title = UIBundle.message("file.cache.conflict.for.file.dialog.title", file.getPresentableUrl());
        final ProjectEx project = (ProjectEx)ProjectLocator.getInstance().guessProjectForFile(file);

        SimpleDiffRequest request = new SimpleDiffRequest(project, title);
        FileType fileType = file.getFileType();
        String fsContent = LoadTextUtil.loadText(file).toString();
        request.setContents(new SimpleContent(fsContent, fileType),
                            new DocumentContent(project, document, fileType));
        request.setContentTitles(UIBundle.message("file.cache.conflict.diff.content.file.system.content"),
                                 UIBundle.message("file.cache.conflict.diff.content.memory.content"));
        DialogBuilder diffBuilder = new DialogBuilder(project);
        DiffPanelImpl diffPanel = (DiffPanelImpl)DiffManager.getInstance().createDiffPanel(diffBuilder.getWindow(), project,diffBuilder);
        diffPanel.getOptions().setShowSourcePolicy(DiffPanelOptions.ShowSourcePolicy.DONT_SHOW);
        diffBuilder.setCenterPanel(diffPanel.getComponent());
        diffPanel.setDiffRequest(request);
        diffBuilder.addOkAction().setText(UIBundle.message("file.cache.conflict.save.changes.button"));
        diffBuilder.addCancelAction();
        diffBuilder.setTitle(title);
        if (diffBuilder.show() == DialogWrapper.OK_EXIT_CODE) {
          builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      }
    });
    builder.setTitle(UIBundle.message("file.cache.conflict.dialog.title"));
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.setHelpId("reference.dialogs.fileCacheConflict");
    return builder.show() == 0;
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    Document doc = getCachedDocument(event.getFile());
    if (doc != null) {
      myTrailingSpacesStripper.documentDeleted(doc);
    }
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
  }

  @Override
  public void fileCopied(VirtualFileCopyEvent event) {
    fileCreated(event);
  }

  @Override
  public void beforePropertyChange(VirtualFilePropertyEvent event) {
  }

  @Override
  public void beforeContentsChange(VirtualFileEvent event) {
  }

  @Override
  public void beforeFileDeletion(VirtualFileEvent event) {
  }

  @Override
  public void beforeFileMovement(VirtualFileMoveEvent event) {
  }

  private void fireUnsavedDocumentsDropped() {
    myMultiCaster.unsavedDocumentsDropped();
  }

  private boolean fireBeforeFileContentReload(final VirtualFile file, @NotNull Document document) {
    for (FileDocumentSynchronizationVetoer vetoer : Extensions.getExtensions(FileDocumentSynchronizationVetoer.EP_NAME)) {
      try {
        if (!vetoer.mayReloadFileContent(file, document)) {
          return false;
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    myMultiCaster.beforeFileContentReload(file, document);
    return true;
  }

  @NotNull
  protected FileDocumentManagerListener[] getListeners() {
    return FileDocumentManagerListener.EP_NAME.getExtensions();
  }

  // todo[r.sh] handle project close/app shutdown
  protected void handleErrorsOnSave(final Map<Document, IOException> failures) {
    final String text = StringUtil.join(failures.values(), new Function<IOException, String>() {
      @Override
      public String fun(IOException e) {
        return e.getMessage();
      }
    }, "\n");

    final DialogWrapper dialog = new DialogWrapper(null) {
      {
        init();
        setTitle(UIBundle.message("cannot.save.files.dialog.title"));
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        myOKAction.putValue(Action.NAME, UIBundle.message("cannot.save.files.dialog.revert.changes"));
        myOKAction.putValue(DEFAULT_ACTION, null);
        myCancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout(0, 5));

        panel.add(new JLabel(UIBundle.message("cannot.save.files.dialog.message")), BorderLayout.NORTH);

        final JTextPane area = new JTextPane();
        area.setText(text);
        area.setEditable(false);
        area.setMinimumSize(new Dimension(area.getMinimumSize().width, 50));
        panel.add(new JBScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

        return panel;
      }
    };

    final AsyncResult<Boolean> result = dialog.showAndGetOk();
    result.doWhenDone(new AsyncResult.Handler<Boolean>() {
      @Override
      public void run(Boolean isOk) {
        if (isOk) {
          for (Document document : failures.keySet()) {
            reloadFromDisk(document);
          }
        }
      }
    });
  }
}
