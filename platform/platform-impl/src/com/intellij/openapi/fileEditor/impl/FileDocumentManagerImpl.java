/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

public class FileDocumentManagerImpl extends FileDocumentManager implements VirtualFileListener, VetoableProjectManagerListener, SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl");

  public static final Key<Document> HARD_REF_TO_DOCUMENT_KEY = Key.create("HARD_REF_TO_DOCUMENT_KEY");

  private static final Key<String> LINE_SEPARATOR_KEY = Key.create("LINE_SEPARATOR_KEY");
  private static final Key<VirtualFile> FILE_KEY = Key.create("FILE_KEY");
  private static final Key<Boolean> MUST_RECOMPUTE_FILE_TYPE = Key.create("Must recompute file type");
  private static final Key<Boolean> BIG_FILE_PREVIEW = Key.create("BIG_FILE_PREVIEW");

  private final Set<Document> myUnsavedDocuments = ContainerUtil.newConcurrentSet();

  private final MessageBus myBus;

  private static final Object lock = new Object();
  private final FileDocumentManagerListener myMultiCaster;
  private final TrailingSpacesStripper myTrailingSpacesStripper = new TrailingSpacesStripper();

  private boolean myOnClose;

  private volatile MemoryDiskConflictResolver myConflictResolver = new MemoryDiskConflictResolver();
  private final PrioritizedDocumentListener myPhysicalDocumentChangeTracker = new PrioritizedDocumentListener() {
    @Override
    public int getPriority() {
      return Integer.MIN_VALUE;
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      final Document document = e.getDocument();
      if (!ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.ExternalDocumentChange.class)) {
        myUnsavedDocuments.add(document);
      }
      final Runnable currentCommand = CommandProcessor.getInstance().getCurrentCommand();
      Project project = currentCommand == null ? null : CommandProcessor.getInstance().getCurrentCommandProject();
      if (project == null)
        project = ProjectUtil.guessProjectForFile(getFile(document));
      String lineSeparator = CodeStyleFacade.getInstance(project).getLineSeparator();
      document.putUserData(LINE_SEPARATOR_KEY, lineSeparator);

      // avoid documents piling up during batch processing
      if (areTooManyDocumentsInTheQueue(myUnsavedDocuments)) {
        saveAllDocumentsLater();
      }
    }
  };

  public FileDocumentManagerImpl(@NotNull VirtualFileManager virtualFileManager, @NotNull ProjectManager projectManager) {
    virtualFileManager.addVirtualFileListener(this);
    projectManager.addProjectManagerListener(this);

    myBus = ApplicationManager.getApplication().getMessageBus();
    myBus.connect().subscribe(ProjectManager.TOPIC, this);

    InvocationHandler handler = (proxy, method, args) -> {
      multiCast(method, args);
      return null;
    };

    final ClassLoader loader = FileDocumentManagerListener.class.getClassLoader();
    myMultiCaster = (FileDocumentManagerListener)Proxy.newProxyInstance(loader, new Class[]{FileDocumentManagerListener.class}, handler);
  }

  private static void unwrapAndRethrow(Exception e) {
    Throwable unwrapped = e;
    if (e instanceof InvocationTargetException) {
      unwrapped = e.getCause() == null ? e : e.getCause();
    }
    ExceptionUtil.rethrowUnchecked(unwrapped);
    LOG.error(unwrapped);
  }

  @SuppressWarnings("OverlyBroadCatchBlock")
  private void multiCast(@NotNull Method method, Object[] args) {
    try {
      method.invoke(myBus.syncPublisher(AppTopics.FILE_DOCUMENT_SYNC), args);
    }
    catch (ClassCastException e) {
      LOG.error("Arguments: "+ Arrays.toString(args), e);
    }
    catch (Exception e) {
      unwrapAndRethrow(e);
    }

    // Allows pre-save document modification
    for (FileDocumentManagerListener listener : getListeners()) {
      try {
        method.invoke(listener, args);
      }
      catch (Exception e) {
        unwrapAndRethrow(e);
      }
    }

    // stripping trailing spaces
    try {
      method.invoke(myTrailingSpacesStripper, args);
    }
    catch (Exception e) {
      unwrapAndRethrow(e);
    }
  }

  @Override
  @Nullable
  public Document getDocument(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    DocumentEx document = (DocumentEx)getCachedDocument(file);
    if (document == null) {
      if (!file.isValid() || file.isDirectory() || isBinaryWithoutDecompiler(file)) return null;

      boolean tooLarge = FileUtilRt.isTooLarge(file.getLength());
      if (file.getFileType().isBinary() && tooLarge) return null;

      final CharSequence text = tooLarge ? LoadTextUtil.loadText(file, getPreviewCharCount(file)) : LoadTextUtil.loadText(file);
      synchronized (lock) {
        document = (DocumentEx)getCachedDocument(file);
        if (document != null) return document; // Double checking

        document = (DocumentEx)createDocument(text, file);
        document.setModificationStamp(file.getModificationStamp());
        document.putUserData(BIG_FILE_PREVIEW, tooLarge ? Boolean.TRUE : null);
        final FileType fileType = file.getFileType();
        document.setReadOnly(tooLarge || !file.isWritable() || fileType.isBinary());

        if (!(file instanceof LightVirtualFile || file.getFileSystem() instanceof NonPhysicalFileSystem)) {
          document.addDocumentListener(myPhysicalDocumentChangeTracker);
        }

        if (file instanceof LightVirtualFile) {
          registerDocument(document, file);
        }
        else {
          document.putUserData(FILE_KEY, file);
          cacheDocument(file, document);
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
      if (totalSize > FileUtilRt.LARGE_FOR_CONTENT_LOADING) return true;
    }
    return false;
  }

  private static Document createDocument(final CharSequence text, VirtualFile file) {
    boolean acceptSlashR = file instanceof LightVirtualFile && StringUtil.indexOf(text, '\r') >= 0;
    boolean freeThreaded = Boolean.TRUE.equals(file.getUserData(SingleRootFileViewProvider.FREE_THREADED));
    return ((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(text, acceptSlashR, freeThreaded);
  }

  @Override
  @Nullable
  public Document getCachedDocument(@NotNull VirtualFile file) {
    Document hard = file.getUserData(HARD_REF_TO_DOCUMENT_KEY);
    return hard != null ? hard : getDocumentFromCache(file);
  }

  public static void registerDocument(@NotNull final Document document, @NotNull VirtualFile virtualFile) {
    synchronized (lock) {
      document.putUserData(FILE_KEY, virtualFile);
      virtualFile.putUserData(HARD_REF_TO_DOCUMENT_KEY, document);
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
    ApplicationManager.getApplication().invokeLater(() -> {
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
    });
  }

  @Override
  public void saveAllDocuments() {
    saveAllDocuments(true);
  }

  /**
   * @param isExplicit caused by user directly (Save action) or indirectly (e.g. Compile)
   */
  public void saveAllDocuments(boolean isExplicit) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

    myMultiCaster.beforeAllDocumentsSaving();
    if (myUnsavedDocuments.isEmpty()) return;

    final Map<Document, IOException> failedToSave = new HashMap<>();
    final Set<Document> vetoed = new HashSet<>();
    while (true) {
      int count = 0;

      for (Document document : myUnsavedDocuments) {
        if (failedToSave.containsKey(document)) continue;
        if (vetoed.contains(document)) continue;
        try {
          doSaveDocument(document, isExplicit);
        }
        catch (IOException e) {
          //noinspection ThrowableResultOfMethodCallIgnored
          failedToSave.put(document, e);
        }
        catch (SaveVetoException e) {
          vetoed.add(document);
        }
        count++;
      }

      if (count == 0) break;
    }

    if (!failedToSave.isEmpty()) {
      handleErrorsOnSave(failedToSave);
    }
  }

  @Override
  public void saveDocument(@NotNull final Document document) {
    saveDocument(document, true);
  }

  public void saveDocument(@NotNull final Document document, final boolean explicit) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

    if (!myUnsavedDocuments.contains(document)) return;

    try {
      doSaveDocument(document, explicit);
    }
    catch (IOException e) {
      handleErrorsOnSave(Collections.singletonMap(document, e));
    }
    catch (SaveVetoException ignored) {
    }
  }

  @Override
  public void saveDocumentAsIs(@NotNull Document document) {
    VirtualFile file = getFile(document);
    boolean spaceStrippingEnabled = true;
    if (file != null) {
      spaceStrippingEnabled = TrailingSpacesStripper.isEnabled(file);
      TrailingSpacesStripper.setEnabled(file, false);
    }
    try {
      saveDocument(document);
    }
    finally {
      if (file != null) {
        TrailingSpacesStripper.setEnabled(file, spaceStrippingEnabled);
      }
    }
  }

  private static class SaveVetoException extends Exception {}

  private void doSaveDocument(@NotNull final Document document, boolean isExplicit) throws IOException, SaveVetoException {
    VirtualFile file = getFile(document);

    if (file == null || file instanceof LightVirtualFile || file.isValid() && !isFileModified(file)) {
      removeFromUnsaved(document);
      return;
    }

    if (file.isValid() && needsRefresh(file)) {
      file.refresh(false, false);
      if (!myUnsavedDocuments.contains(document)) return;
    }

    if (!maySaveDocument(file, document, isExplicit)) {
      throw new SaveVetoException();
    }

    WriteAction.run(() -> doSaveDocumentInWriteAction(document, file));
  }

  private boolean maySaveDocument(VirtualFile file, Document document, boolean isExplicit) {
    return !myConflictResolver.hasConflict(file) &&
           Arrays.stream(Extensions.getExtensions(FileDocumentSynchronizationVetoer.EP_NAME)).allMatch(vetoer -> vetoer.maySaveDocument(document, isExplicit));
  }

  private void doSaveDocumentInWriteAction(@NotNull final Document document, @NotNull final VirtualFile file) throws IOException {
    if (!file.isValid()) {
      removeFromUnsaved(document);
      return;
    }

    if (!file.equals(getFile(document))) {
      registerDocument(document, file);
    }

    boolean saveNeeded = false;
    IOException ioException = null;
    try {
      saveNeeded = isSaveNeeded(document, file);
    }
    catch (IOException e) {
      // in case of corrupted VFS try to stay consistent
      ioException = e;
    }
    if (!saveNeeded) {
      if (document instanceof DocumentEx) {
        ((DocumentEx)document).setModificationStamp(file.getModificationStamp());
      }
      removeFromUnsaved(document);
      updateModifiedProperty(file);
      if (ioException != null) throw ioException;
      return;
    }

    PomModelImpl.guardPsiModificationsIn(() -> {
      myMultiCaster.beforeDocumentSaving(document);
      LOG.assertTrue(file.isValid());

      String text = document.getText();
      String lineSeparator = getLineSeparator(document, file);
      if (!lineSeparator.equals("\n")) {
        text = StringUtil.convertLineSeparators(text, lineSeparator);
      }

      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      LoadTextUtil.write(project, file, this, text, document.getModificationStamp());

      myUnsavedDocuments.remove(document);
      LOG.assertTrue(!myUnsavedDocuments.contains(document));
      myTrailingSpacesStripper.clearLineModificationFlags(document);
    });
  }

  private static void updateModifiedProperty(@NotNull VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      for (FileEditor editor : fileEditorManager.getAllEditors(file)) {
        if (editor instanceof TextEditorImpl) {
          ((TextEditorImpl)editor).updateModifiedProperty();
        }
      }
    }
  }

  private void removeFromUnsaved(@NotNull Document document) {
    myUnsavedDocuments.remove(document);
    fireUnsavedDocumentsDropped();
    LOG.assertTrue(!myUnsavedDocuments.contains(document));
  }

  private static boolean isSaveNeeded(@NotNull Document document, @NotNull VirtualFile file) throws IOException {
    if (file.getFileType().isBinary() || document.getTextLength() > 1000 * 1000) {    // don't compare if the file is too big
      return true;
    }

    byte[] bytes = file.contentsToByteArray();
    CharSequence loaded = LoadTextUtil.getTextByBinaryPresentation(bytes, file, false, false);

    return !Comparing.equal(document.getCharsSequence(), loaded);
  }

  private static boolean needsRefresh(final VirtualFile file) {
    final VirtualFileSystem fs = file.getFileSystem();
    return fs instanceof NewVirtualFileSystem && file.getTimeStamp() != ((NewVirtualFileSystem)fs).getTimeStamp(file);
  }

  @NotNull
  public static String getLineSeparator(@NotNull Document document, @NotNull VirtualFile file) {
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
    String lineSeparator = file == null ? null : LoadTextUtil.getDetectedLineSeparator(file);
    if (lineSeparator == null) {
      CodeStyleFacade settingsManager = project == null
                                        ? CodeStyleFacade.getInstance()
                                        : CodeStyleFacade.getInstance(project);
      lineSeparator = settingsManager.getLineSeparator();
    }
    return lineSeparator;
  }

  @Override
  public boolean requestWriting(@NotNull Document document, Project project) {
    final VirtualFile file = getInstance().getFile(document);
    if (project != null && file != null && file.isValid()) {
      return !file.getFileType().isBinary() && ReadonlyStatusHandler.ensureFilesWritable(project, file);
    }
    if (document.isWritable()) {
      return true;
    }
    document.fireReadOnlyModificationAttempt();
    return false;
  }

  @Override
  public void reloadFiles(@NotNull final VirtualFile... files) {
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

    List<Document> list = new ArrayList<>(myUnsavedDocuments);
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
  public boolean isPartialPreviewOfALargeFile(@NotNull Document document) {
    return document.getUserData(BIG_FILE_PREVIEW) == Boolean.TRUE;
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    final VirtualFile file = event.getFile();
    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      final Document document = getCachedDocument(file);
      if (document != null) {
        ApplicationManager.getApplication().runWriteAction((ExternalChangeAction)() -> document.setReadOnly(!file.isWritable()));
      }
    }
    else if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      Document document = getCachedDocument(file);
      if (document != null) {
        // a file is linked to a document - chances are it is an "unknown text file" now
        if (isBinaryWithoutDecompiler(file)) {
          unbindFileFromDocument(file, document);
        }
      }
    }
  }

  private void unbindFileFromDocument(@NotNull VirtualFile file, @NotNull Document document) {
    removeDocumentFromCache(file);
    file.putUserData(HARD_REF_TO_DOCUMENT_KEY, null);
    document.putUserData(FILE_KEY, null);
  }

  private static boolean isBinaryWithDecompiler(@NotNull VirtualFile file) {
    final FileType ft = file.getFileType();
    return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) != null;
  }

  private static boolean isBinaryWithoutDecompiler(@NotNull VirtualFile file) {
    final FileType fileType = file.getFileType();
    return fileType.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(fileType) == null;
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    if (event.isFromSave()) return;
    final VirtualFile file = event.getFile();
    final Document document = getCachedDocument(file);
    if (document == null) {
      myMultiCaster.fileWithNoDocumentChanged(file);
      return;
    }

    if (isBinaryWithDecompiler(file)) {
      myMultiCaster.fileWithNoDocumentChanged(file); // This will generate PSI event at FileManagerImpl
    }

    if (document.getModificationStamp() == event.getOldModificationStamp() || !isDocumentUnsaved(document)) {
      reloadFromDisk(document);
    }
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
    boolean[] isReloadable = {isReloadable(file, document, project)};
    if (isReloadable[0]) {
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction.ExternalDocumentChange(document, project) {
          @Override
          public void run() {
            if (!isBinaryWithoutDecompiler(file)) {
              LoadTextUtil.setCharsetWasDetectedFromBytes(file, null);
              file.setBOM(null); // reset BOM in case we had one and the external change stripped it away
              file.setCharset(null, null, false);
              boolean wasWritable = document.isWritable();
              document.setReadOnly(false);
              boolean tooLarge = FileUtilRt.isTooLarge(file.getLength());
              isReloadable[0] = isReloadable(file, document, project);
              if (isReloadable[0]) {
                CharSequence reloaded = tooLarge ? LoadTextUtil.loadText(file, getPreviewCharCount(file)) : LoadTextUtil.loadText(file);
                ((DocumentEx)document).replaceText(reloaded, file.getModificationStamp());
                document.putUserData(BIG_FILE_PREVIEW, tooLarge ? Boolean.TRUE : null);
              }
              document.setReadOnly(!wasWritable);
            }
          }
        }
      ), UIBundle.message("file.cache.conflict.action"), null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
    }
    if (isReloadable[0]) {
      myMultiCaster.fileContentReloaded(file, document);
    }
    else {
      unbindFileFromDocument(file, document);
      myMultiCaster.fileWithNoDocumentChanged(file);
    }
    myUnsavedDocuments.remove(document);
  }

  private static boolean isReloadable(@NotNull VirtualFile file, @NotNull Document document, @Nullable Project project) {
    PsiFile cachedPsiFile = project == null ? null : PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    return !(FileUtilRt.isTooLarge(file.getLength()) && file.getFileType().isBinary()) &&
           (cachedPsiFile == null || cachedPsiFile instanceof PsiFileImpl || isBinaryWithDecompiler(file));
  }

  @TestOnly
  void setAskReloadFromDisk(@NotNull Disposable disposable, @NotNull MemoryDiskConflictResolver newProcessor) {
    final MemoryDiskConflictResolver old = myConflictResolver;
    myConflictResolver = newProcessor;
    Disposer.register(disposable, () -> myConflictResolver = old);
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    Document doc = getCachedDocument(event.getFile());
    if (doc != null) {
      myTrailingSpacesStripper.documentDeleted(doc);
    }
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    VirtualFile virtualFile = event.getFile();
    // check file type in second order to avoid content detection running
    if (virtualFile.getLength() == 0 && virtualFile.getFileType() == UnknownFileType.INSTANCE) {
      virtualFile.putUserData(MUST_RECOMPUTE_FILE_TYPE, Boolean.TRUE);
    }

    if(ourConflictsSolverEnabled)
      myConflictResolver.beforeContentChange(event);
  }

  public static boolean recomputeFileTypeIfNecessary(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getUserData(MUST_RECOMPUTE_FILE_TYPE) != null) {
      virtualFile.getFileType();
      virtualFile.putUserData(MUST_RECOMPUTE_FILE_TYPE, null);
      return true;
    }
    return false;
  }

  @Override
  public boolean canClose(@NotNull Project project) {
    if (!myUnsavedDocuments.isEmpty()) {
      myOnClose = true;
      try {
        saveAllDocuments();
      }
      finally {
        myOnClose = false;
      }
    }
    return myUnsavedDocuments.isEmpty();
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
  private static FileDocumentManagerListener[] getListeners() {
    return FileDocumentManagerListener.EP_NAME.getExtensions();
  }

  private static int getPreviewCharCount(@NotNull VirtualFile file) {
    Charset charset = EncodingManager.getInstance().getEncoding(file, false);
    float bytesPerChar = charset == null ? 2 : charset.newEncoder().averageBytesPerChar();
    return (int)(FileUtilRt.LARGE_FILE_PREVIEW_SIZE / bytesPerChar);
  }

  private void handleErrorsOnSave(@NotNull Map<Document, IOException> failures) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      IOException ioException = ContainerUtil.getFirstItem(failures.values());
      if (ioException != null) {
        throw new RuntimeException(ioException);
      }
      return;
    }
    for (IOException exception : failures.values()) {
      LOG.warn(exception);
    }

    final String text = StringUtil.join(failures.values(), Throwable::getMessage, "\n");

    final DialogWrapper dialog = new DialogWrapper(null) {
      {
        init();
        setTitle(UIBundle.message("cannot.save.files.dialog.title"));
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        myOKAction.putValue(Action.NAME, UIBundle
          .message(myOnClose ? "cannot.save.files.dialog.ignore.changes" : "cannot.save.files.dialog.revert.changes"));
        myOKAction.putValue(DEFAULT_ACTION, null);

        if (!myOnClose) {
          myCancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
        }
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout(0, 5));

        panel.add(new JLabel(UIBundle.message("cannot.save.files.dialog.message")), BorderLayout.NORTH);

        final JTextPane area = new JTextPane();
        area.setText(text);
        area.setEditable(false);
        area.setMinimumSize(new Dimension(area.getMinimumSize().width, 50));
        panel.add(new JBScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
                  BorderLayout.CENTER);

        return panel;
      }
    };

    if (dialog.showAndGet()) {
      for (Document document : failures.keySet()) {
        reloadFromDisk(document);
      }
    }
  }

  private final Map<VirtualFile, Document> myDocumentCache = ContainerUtil.createConcurrentWeakValueMap();

  //temp setter for Rider 2017.1
  public static boolean ourConflictsSolverEnabled = true;

  // used in Upsource
  protected void cacheDocument(@NotNull VirtualFile file, @NotNull Document document) {
    myDocumentCache.put(file, document);
  }

  // used in Upsource
  protected void removeDocumentFromCache(@NotNull VirtualFile file) {
    myDocumentCache.remove(file);
  }

  // used in Upsource
  protected Document getDocumentFromCache(@NotNull VirtualFile file) {
    return myDocumentCache.get(file);
  }
}
