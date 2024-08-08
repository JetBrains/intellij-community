// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.CommonBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.converter.FileTextConverter;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

public class FileDocumentManagerImpl extends FileDocumentManagerBase implements SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance(FileDocumentManagerImpl.class);

  public static final Key<Object> NOT_RELOADABLE_DOCUMENT_KEY = new Key<>("NOT_RELOADABLE_DOCUMENT_KEY");

  private static final Key<String> LINE_SEPARATOR_KEY = Key.create("LINE_SEPARATOR_KEY");
  private static final Key<Boolean> MUST_RECOMPUTE_FILE_TYPE = Key.create("Must recompute file type");

  private final Set<Document> myUnsavedDocuments = ConcurrentCollectionFactory.createConcurrentSet();

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
    public void documentChanged(@NotNull DocumentEvent e) {
      Document document = e.getDocument();
      markDocumentUnsaved(document);
      Runnable currentCommand = CommandProcessor.getInstance().getCurrentCommand();
      Project project = currentCommand == null ? null : CommandProcessor.getInstance().getCurrentCommandProject();
      VirtualFile virtualFile = getFile(document);
      if (project == null) {
        project = virtualFile == null ? null : ProjectUtil.guessProjectForFile(virtualFile);
      }
      CodeStyleSettings settings = project != null && virtualFile != null
                                   ? CodeStyle.getSettings(project, virtualFile)
                                   : CodeStyle.getProjectOrDefaultSettings(project);
      String lineSeparator = settings.getLineSeparator();
      document.putUserData(LINE_SEPARATOR_KEY, lineSeparator);

      // avoid documents piling up during batch processing
      if (areTooManyDocumentsInTheQueue(myUnsavedDocuments)) {
        saveAllDocumentsLater();
      }
    }
  };

  public FileDocumentManagerImpl() {
    InvocationHandler handler = (__, method, args) -> {
      if (method.getDeclaringClass() != FileDocumentManagerListener.class) {
        // only FileDocumentManagerListener methods should be called on this proxy
        throw new UnsupportedOperationException(method.toString());
      }
      multiCast(method, args);
      return null;
    };

    myMultiCaster = ReflectionUtil.proxy(FileDocumentManagerListener.class, handler);

    // remove VirtualFiles sitting in the DocumentImpl.rmTreeQueue reference queue which could retain plugin-registered FS in their VirtualDirectoryImpl.myFs
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        DocumentImpl.processQueue();
      }
    });
  }

  static final class MyProjectCloseHandler implements ProjectCloseHandler {
    @Override
    public boolean canClose(@NotNull Project project) {
      FileDocumentManagerImpl manager = (FileDocumentManagerImpl)getInstance();
      if (!manager.myUnsavedDocuments.isEmpty()) {
        manager.myOnClose = true;
        try {
          manager.saveAllDocuments();
        }
        finally {
          manager.myOnClose = false;
        }
      }
      return manager.myUnsavedDocuments.isEmpty();
    }
  }

  private static void unwrapAndRethrow(@NotNull Exception e) {
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
      method.invoke(ApplicationManager.getApplication().getMessageBus().syncPublisher(FileDocumentManagerListener.TOPIC), args);
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

  public static boolean areTooManyDocumentsInTheQueue(@NotNull Collection<? extends Document> documents) {
    if (documents.size() > 100) return true;
    int totalSize = 0;
    for (Document document : documents) {
      totalSize += document.getTextLength();
      if (totalSize > FileUtilRt.LARGE_FOR_CONTENT_LOADING) return true;
    }
    return false;
  }

  @ApiStatus.Internal
  public void markDocumentUnsaved(Document document) {
    if (!ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.ExternalDocumentChange.class)) {
      myUnsavedDocuments.add(document);
    }
  }

  @Override
  protected @NotNull DocumentEx createDocument(@NotNull CharSequence text, @NotNull VirtualFile file) {
    boolean acceptSlashR = file instanceof LightVirtualFile && StringUtil.indexOf(text, '\r') >= 0;
    boolean freeThreaded = Boolean.TRUE.equals(file.getUserData(AbstractFileViewProvider.FREE_THREADED));
    DocumentImpl document = (DocumentImpl)((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(text, acceptSlashR, freeThreaded);
    Project project = ProjectUtil.guessProjectForFile(file);
    int tabSize = project == null ? CodeStyle.getDefaultSettings().getTabSize(file.getFileType())  : CodeStyle.getFacade(project, document, file.getFileType()).getTabSize();
    // calculate and pass tab size here since it's the ony place we have access to CodeStyle.
    // tabSize might be needed by PersistentRangeMarkers to be able to restore from (line;col) info to offset
    document.documentCreatedFrom(file, tabSize);
    return document;
  }

  @TestOnly
  public void dropAllUnsavedDocuments() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("This method is only for test mode!");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!myUnsavedDocuments.isEmpty()) {
      for (Document document : myUnsavedDocuments) {
        VirtualFile file = getFile(document);
        if (file == null) continue;
        unbindFileFromDocument(file, document);
      }
      myUnsavedDocuments.clear();
      myMultiCaster.unsavedDocumentsDropped();
    }
  }

  private void saveAllDocumentsLater() {
    // later because some document might have been blocked by PSI right now
    ApplicationManager.getApplication().invokeLater(() -> {
      Document[] unsavedDocuments = getUnsavedDocuments();
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
    saveDocuments(null, isExplicit);
  }

  @Override
  public void saveDocuments(@NotNull Predicate<? super Document> filter) {
    saveDocuments(filter, true);
  }

  private void saveDocuments(@Nullable Predicate<? super Document> filter, boolean isExplicit) {
    ThreadingAssertions.assertEventDispatchThread();
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

    myMultiCaster.beforeAllDocumentsSaving();
    if (myUnsavedDocuments.isEmpty()) return;

    Map<Document, IOException> failedToSave = new HashMap<>();
    Set<Document> vetoed = new HashSet<>();
    while (true) {
      int count = 0;

      for (Document document : myUnsavedDocuments) {
        if (filter != null && !filter.test(document)) continue;
        if (failedToSave.containsKey(document)) continue;
        if (vetoed.contains(document)) continue;
        try {
          doSaveDocument(document, isExplicit);
        }
        catch (IOException e) {
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
  public void saveDocument(@NotNull Document document) {
    saveDocument(document, true);
  }

  public void saveDocument(@NotNull Document document, boolean explicit) {
    ThreadingAssertions.assertEventDispatchThread();
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();

    myMultiCaster.beforeAnyDocumentSaving(document, explicit);
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

  private static final class SaveVetoException extends Exception {}

  private void doSaveDocument(@NotNull Document document, boolean isExplicit) throws IOException, SaveVetoException {
    VirtualFile file = getFile(document);
    if (LOG.isTraceEnabled()) LOG.trace("saving: " + file);

    if (file == null ||
        !isTrackable(file) ||
        file.isValid() && !isFileModified(file)) {
      removeFromUnsaved(document);
      return;
    }

    if (file.isValid() && needsRefresh(file)) {
      LOG.trace("  refreshing...");
      file.refresh(false, false);
      if (!myUnsavedDocuments.contains(document)) return;
    }

    if (!maySaveDocument(file, document, isExplicit)) {
      throw new SaveVetoException();
    }

    LOG.trace("  writing...");
    WriteAction.run(() -> doSaveDocumentInWriteAction(document, file));
    LOG.trace("  done");
  }

  private boolean maySaveDocument(@NotNull VirtualFile file, @NotNull Document document, boolean isExplicit) {
    if (myConflictResolver.hasConflict(file)) {
      return false;
    }

    for (FileDocumentSynchronizationVetoer vetoer : FileDocumentSynchronizationVetoer.EP_NAME.getExtensionList()) {
      if (!vetoer.maySaveDocument(document, isExplicit)) {
        return false;
      }
    }
    return true;
  }

  private void doSaveDocumentInWriteAction(@NotNull Document document, @NotNull VirtualFile file) throws IOException {
    if (!file.isValid()) {
      removeFromUnsaved(document);
      return;
    }

    if (!file.equals(getFile(document))) {
      registerDocument(document, file);
    }

    boolean saveNeeded = false;
    Exception ioException = null;
    try {
      saveNeeded = isSaveNeeded(document, file);
    }
    catch (IOException|RuntimeException e) {
      // in case of corrupted VFS try to stay consistent
      ioException = e;
    }
    if (!saveNeeded) {
      if (document instanceof DocumentEx) {
        ((DocumentEx)document).setModificationStamp(file.getModificationStamp());
      }
      removeFromUnsaved(document);
      updateModifiedProperty(file);
      if (ioException instanceof IOException) throw (IOException)ioException;
      if (ioException != null) throw (RuntimeException)ioException;
      return;
    }

    PomModelImpl.guardPsiModificationsIn(() -> {
      myMultiCaster.beforeDocumentSaving(document);
      LOG.assertTrue(file.isValid());

      String text = document.getText();
      String lineSeparator = getLineSeparator(document, file);

      //Some files have document.text different from file representation
      text = FileTextConverter.convertToSaveDocumentTextToFile(text, file);

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
      for (FileEditor editor : fileEditorManager.getAllEditorList(file)) {
        if (editor instanceof TextEditorImpl) {
          ((TextEditorImpl)editor).updateModifiedProperty();
        }
      }
    }
  }

  private void removeFromUnsaved(@NotNull Document document) {
    myUnsavedDocuments.remove(document);
    myMultiCaster.unsavedDocumentDropped(document);
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

  private static boolean needsRefresh(@NotNull VirtualFile file) {
    VirtualFileSystem fs = file.getFileSystem();
    return fs instanceof NewVirtualFileSystem && file.getTimeStamp() != ((NewVirtualFileSystem)fs).getTimeStamp(file);
  }

  public static @NotNull String getLineSeparator(@NotNull Document document, @NotNull VirtualFile file) {
    String lineSeparator = file.getDetectedLineSeparator();
    if (lineSeparator == null) {
      lineSeparator = document.getUserData(LINE_SEPARATOR_KEY);
      assert lineSeparator != null : document;
    }
    return lineSeparator;
  }

  @Override
  public @NotNull String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project) {
    String lineSeparator = file == null ? null : file.getDetectedLineSeparator();
    if (lineSeparator == null) {
      CodeStyleSettings settings =
        project != null && file != null ? CodeStyle.getSettings(project, file) : CodeStyle.getProjectOrDefaultSettings(project);
      lineSeparator = settings.getLineSeparator();
    }
    return lineSeparator;
  }

  @Override
  public boolean requestWriting(@NotNull Document document, Project project) {
    return requestWritingStatus(document, project).hasWriteAccess();
  }

  @Override
  public @NotNull WriteAccessStatus requestWritingStatus(@NotNull Document document, @Nullable Project project) {
    VirtualFile file = getInstance().getFile(document);
    if (project != null && file != null && file.isValid()) {
      if (file.getFileType().isBinary()) return WriteAccessStatus.NON_WRITABLE;
      ReadonlyStatusHandler.OperationStatus writableStatus =
        ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Collections.singletonList(file));
      if (writableStatus.hasReadonlyFiles()) {
        return new WriteAccessStatus(writableStatus.getReadonlyFilesMessage(), writableStatus.getHyperlinkListener());
      }
      assert file.isWritable() : file;
    }
    if (document.isWritable()) {
      return WriteAccessStatus.WRITABLE;
    }
    document.fireReadOnlyModificationAttempt();
    return WriteAccessStatus.NON_WRITABLE;
  }

  @Override
  public void reloadFiles(VirtualFile @NotNull ... files) {
    for (VirtualFile file : files) {
      if (file.exists()) {
        Document doc = getCachedDocument(file);
        if (doc != null) {
          reloadFromDisk(doc);
        }
      }
    }
  }

  @Override
  public Document @NotNull [] getUnsavedDocuments() {
    if (myUnsavedDocuments.isEmpty()) {
      return Document.EMPTY_ARRAY;
    }

    List<Document> list = new ArrayList<>(myUnsavedDocuments);
    return list.toArray(Document.EMPTY_ARRAY);
  }

  @Override
  public boolean processUnsavedDocuments(Processor<? super Document> processor) {
    for (Document doc : myUnsavedDocuments) {
      if (!processor.process(doc)) return false;
    }

    return true;
  }

  @Override
  public boolean isDocumentUnsaved(@NotNull Document document) {
    return myUnsavedDocuments.contains(document);
  }

  @Override
  public boolean isFileModified(@NotNull VirtualFile file) {
    Document doc = getCachedDocument(file);
    return doc != null && isDocumentUnsaved(doc) && doc.getModificationStamp() != file.getModificationStamp();
  }

  private void propertyChanged(@NotNull VFilePropertyChangeEvent event) {
    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      VirtualFile file = event.getFile();
      Document document = getCachedDocument(file);
      if (document != null) {
        ApplicationManager.getApplication().runWriteAction((ExternalChangeAction)() -> document.setReadOnly(!file.isWritable()));
      }
    }
    else if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      VirtualFile file = event.getFile();
      Document document = getCachedDocument(file);
      if (document == null) {
        return;
      }
      if (isBinaryWithoutDecompiler(file)) {
        // a file is linked to a document - chances are it is an "unknown text file" now
        unbindFileFromDocument(file, document);
        // to avoid weird inconsistencies when file opened in an editor tab got renamed to unknown extension and then typed into
        closeAllEditorsFor(file);
        myMultiCaster.afterDocumentUnbound(file, document);
      }
      else if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor()) && isBinaryWithDecompiler(file)) {
        reloadFromDisk(document);
      }
    }
  }

  private static void closeAllEditorsFor(@NotNull VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
  }

  private static boolean isBinaryWithDecompiler(@NotNull VirtualFile file) {
    FileType type = file.getFileType();
    return type.isBinary() && BinaryFileTypeDecompilers.getInstance().forFileType(type) != null;
  }

  static final class MyAsyncFileListener implements AsyncFileListener {
    private final FileDocumentManagerImpl myFileDocumentManager = (FileDocumentManagerImpl)getInstance();

    @Override
    public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      List<VirtualFile> toRecompute = new ArrayList<>();
      Map<VirtualFile, Document> strongRefsToDocuments = new HashMap<>();
      List<VFileContentChangeEvent> contentChanges = ContainerUtil.findAll(events, VFileContentChangeEvent.class);
      for (VFileContentChangeEvent event : contentChanges) {
        ProgressManager.checkCanceled();
        VirtualFile virtualFile = event.getFile();

        // when an empty unknown file is written into, re-run file type detection
        if (virtualFile instanceof VirtualFileWithId) {
          long lastRecordedLength = PersistentFS.getInstance().getLastRecordedLength(virtualFile);
          if (lastRecordedLength == 0 &&
              FileTypeRegistry.getInstance().isFileOfType(virtualFile, UnknownFileType.INSTANCE)) { // check file type last to avoid content detection running
            toRecompute.add(virtualFile);
          }
        }

        prepareForRangeMarkerUpdate(strongRefsToDocuments, virtualFile);
      }

      return new ChangeApplier() {
        @Override
        public void beforeVfsChange() {
          for (VFileContentChangeEvent event : contentChanges) {
            // new range markers could've appeared after "prepareChange" in some read action
            prepareForRangeMarkerUpdate(strongRefsToDocuments, event.getFile());
            if (ourConflictsSolverEnabled) {
              myFileDocumentManager.myConflictResolver.beforeContentChange(event);
            }
          }

          for (VirtualFile file : toRecompute) {
            file.putUserData(MUST_RECOMPUTE_FILE_TYPE, Boolean.TRUE);
          }
        }

        @Override
        public void afterVfsChange() {
          for (VFileEvent event : events) {
            if (event instanceof VFileContentChangeEvent && ((VFileContentChangeEvent)event).getFile().isValid()) {
              myFileDocumentManager.contentsChanged((VFileContentChangeEvent)event);
            }
            else if (event instanceof VFileDeleteEvent && ((VFileDeleteEvent)event).getFile().isValid()) {
              myFileDocumentManager.fileDeleted((VFileDeleteEvent)event);
            }
            else if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).getFile().isValid()) {
              myFileDocumentManager.propertyChanged((VFilePropertyChangeEvent)event);
            }
          }
          Reference.reachabilityFence(strongRefsToDocuments);
        }
      };
    }

    private void prepareForRangeMarkerUpdate(@NotNull Map<? super VirtualFile, ? super Document> strongRefsToDocuments,
                                             @NotNull VirtualFile virtualFile) {
      Document document = myFileDocumentManager.getCachedDocument(virtualFile);
      if (document == null && DocumentImpl.areRangeMarkersRetainedFor(virtualFile)) {
        // re-create document with the old contents prior to this event
        // then contentChanged() will diff the document with the new contents and update the markers
        document = myFileDocumentManager.getDocument(virtualFile);
      }
      // save document strongly to make it live until contentChanged()
      if (document != null) {
        strongRefsToDocuments.put(virtualFile, document);
      }
    }
  }

  public void contentsChanged(@NotNull VFileContentChangeEvent event) {
    VirtualFile virtualFile = event.getFile();
    Document document = getCachedDocument(virtualFile);

    boolean shouldTraceEvent = document != null && LOG.isTraceEnabled();
    String eventMessage = null;

    if (shouldTraceEvent) {
      eventMessage = "content changed for " + event.getFile() + " with document stamp =  " + document.getModificationStamp();
    }

    if (event.isFromSave()) {
      if (shouldTraceEvent) {
        eventMessage += " , dispatched from save";
        LOG.trace(eventMessage);
      }

      return;
    }

    if (document == null || isBinaryWithDecompiler(virtualFile)) {
      myMultiCaster.fileWithNoDocumentChanged(virtualFile); // This will generate PSI event at FileManagerImpl
    }

    if (document != null) {
      if (shouldTraceEvent) {
        eventMessage += " event old modification stamp = " + event.getOldModificationStamp() + ", is unsaved = " + isDocumentUnsaved(document);
        LOG.trace(eventMessage);
      }

      if (document.getModificationStamp() == event.getOldModificationStamp() || !isDocumentUnsaved(document)) {
        reloadFromDisk(document);
      }
    }
  }

  @Override
  public void reloadFromDisk(@NotNull Document document, @Nullable Project project) {
    try (AccessToken ignored = ClientId.withClientId(ClientId.getLocalId())) {
      ThreadingAssertions.assertEventDispatchThread();

      VirtualFile file = getFile(document);
      assert file != null;
      if (!file.isValid()) return;

      if (!fireBeforeFileContentReload(file, document)) {
        return;
      }

      boolean[] isReloadable = {isReloadable(file, document, project)};
      if (isReloadable[0]) {
        CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(
          new ExternalChangeAction.ExternalDocumentChange(document, project) {
            @Override
            public void run() {
              if (!isBinaryWithoutDecompiler(file)) {
                LoadTextUtil.clearCharsetAutoDetectionReason(file);
                file.setBOM(null); // reset BOM in case we had one and the external change stripped it away
                file.setCharset(null, null, false);
                boolean wasWritable = document.isWritable();
                document.setReadOnly(false);
                boolean tooLarge = FileUtilRt.isTooLarge(file.getLength());
                isReloadable[0] = isReloadable(file, document, project);
                if (isReloadable[0]) {
                  CharSequence reloaded = tooLarge ? LoadTextUtil.loadText(file, getPreviewCharCount(file)) : LoadTextUtil.loadText(file);
                  ((DocumentEx)document).replaceText(reloaded, file.getModificationStamp());
                  setDocumentTooLarge(document, tooLarge);
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
        myMultiCaster.afterDocumentUnbound(file, document);
      }
      myUnsavedDocuments.remove(document);
    }
  }

  private static boolean isReloadable(@NotNull VirtualFile file, @NotNull Document document, @Nullable Project project) {
    PsiFile cachedPsiFile = project == null ? null : PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    return !(FileUtilRt.isTooLarge(file.getLength()) && file.getFileType().isBinary()) &&
           (cachedPsiFile == null || cachedPsiFile instanceof PsiFileImpl || isBinaryWithDecompiler(file)) &&
           document.getUserData(NOT_RELOADABLE_DOCUMENT_KEY) == null;
  }

  @TestOnly
  void setAskReloadFromDisk(@NotNull Disposable disposable, @NotNull MemoryDiskConflictResolver newProcessor) {
    MemoryDiskConflictResolver old = myConflictResolver;
    myConflictResolver = newProcessor;
    Disposer.register(disposable, () -> myConflictResolver = old);
  }

  private void fileDeleted(@NotNull VFileDeleteEvent event) {
    VirtualFile virtualFile = event.getFile();
    Document doc = getCachedDocument(virtualFile);
    if (doc != null) {
      myTrailingSpacesStripper.documentDeleted(doc);
      unbindFileFromDocument(virtualFile, doc);
    }
  }

  public static boolean recomputeFileTypeIfNecessary(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getUserData(MUST_RECOMPUTE_FILE_TYPE) != null) {
      virtualFile.getFileType();
      virtualFile.putUserData(MUST_RECOMPUTE_FILE_TYPE, null);
      return true;
    }
    return false;
  }

  private boolean fireBeforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) {
    for (FileDocumentSynchronizationVetoer vetoer : FileDocumentSynchronizationVetoer.EP_NAME.getExtensionList()) {
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

  private static @NotNull List<FileDocumentManagerListener> getListeners() {
    return FileDocumentManagerListener.EP_NAME.getExtensionList();
  }

  @Override
  public @Nullable FileViewProvider findCachedPsiInAnyProject(@NotNull VirtualFile file) {
    ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
    for (Project project : manager.getOpenProjects()) {
      FileViewProvider vp = PsiManagerEx.getInstanceEx(project).getFileManager().findCachedViewProvider(file);
      if (vp != null) return vp;
    }

    if (manager.isDefaultProjectInitialized()) {
      FileViewProvider vp = PsiManagerEx.getInstanceEx(manager.getDefaultProject()).getFileManager().findCachedViewProvider(file);
      if (vp != null) return vp;
    }

    return null;
  }

  private void handleErrorsOnSave(@NotNull Map<Document, IOException> failures) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      IOException ioException = ContainerUtil.getFirstItem(failures.values());
      if (ioException != null) throw new RuntimeException(ioException);
      return;
    }

    for (Map.Entry<Document, IOException> entry : failures.entrySet()) {
      LOG.warn("file: " + getFile(entry.getKey()), entry.getValue());
    }

    // later to get out of write action
    ApplicationManager.getApplication().invokeLater(() -> {
      String text = StringUtil.join(failures.values(), Throwable::getMessage, "\n");

      DialogWrapper dialog = new DialogWrapper((Project)null) {
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
          JPanel panel = new JPanel(new BorderLayout(0, 5));

          panel.add(new JLabel(UIBundle.message("cannot.save.files.dialog.message")), BorderLayout.NORTH);

          JTextPane area = new JTextPane();
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
    });
  }

  /** @deprecated another dirty Rider hack; don't use */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("StaticNonFinalField")
  public static boolean ourConflictsSolverEnabled = true;

  @Override
  protected void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
    myMultiCaster.fileContentLoaded(file, document);
  }

  @Override
  protected @NotNull DocumentListener getDocumentListener() {
    return myPhysicalDocumentChangeTracker;
  }

  @ApiStatus.Internal
  static final class MyPersistentFsConnectionListener implements PersistentFsConnectionListener {
    @Override
    public void connectionOpen() {
      FileDocumentManagerImpl fileDocumentManager =
        (FileDocumentManagerImpl)ApplicationManager.getApplication().getServiceIfCreated(FileDocumentManager.class);
      if (fileDocumentManager != null) {
        fileDocumentManager.clearDocumentCache();
      }
    }
  }
}
