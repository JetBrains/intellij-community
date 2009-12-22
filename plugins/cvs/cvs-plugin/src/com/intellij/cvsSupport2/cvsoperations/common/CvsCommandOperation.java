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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.connections.pserver.PServerCvsSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsExecution.ModalityContextImpl;
import com.intellij.cvsSupport2.cvsIgnore.IgnoreFileFilterBasedOnCvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesTranslator;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.FileMessage;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderOnCache;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminWriterOnCache;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.javacvsImpl.FileReadOnlyHandler;
import com.intellij.cvsSupport2.javacvsImpl.ProjectContentInfoProvider;
import com.intellij.cvsSupport2.javacvsImpl.io.*;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.ClientEnvironment;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.RequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.event.*;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.IFileSystem;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;
import org.netbeans.lib.cvsclient.file.ILocalFileWriter;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class CvsCommandOperation extends CvsOperation implements IFileInfoListener,
                                                                          IMessageListener,
                                                                          IEntryListener,
                                                                          IModuleExpansionListener,
                                                                          ProjectContentInfoProvider {

  private static final IAdminReader DEFAULT_ADMIN_READER = new AdminReaderOnCache();

  protected final IAdminReader myAdminReader;
  protected final IAdminWriter myAdminWriter;

  protected static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation");
  private String myLastProcessedCvsRoot;
  private final UpdatedFilesManager myUpdatedFilesManager = new UpdatedFilesManager();

  protected CvsCommandOperation() {
    this(DEFAULT_ADMIN_READER);
  }

  protected CvsCommandOperation(IAdminReader adminReader, IAdminWriter adminWriter) {
    myAdminReader = adminReader;
    myAdminWriter = adminWriter;
  }

  protected CvsCommandOperation(IAdminReader adminReader) {
    myAdminReader = adminReader;
    myAdminWriter = new AdminWriterOnCache(myUpdatedFilesManager, this);
  }

  abstract protected Command createCommand(CvsRootProvider root,
                                           CvsExecutionEnvironment cvsExecutionEnvironment);

  public void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException,
                                                                           CommandAbortedException {
    CvsEntriesManager.getInstance().lockSynchronizationActions();
    try {
      if (runInExclusiveLock()) {
        synchronized (CvsOperation.class) {
          doExecute(executionEnvironment);
        }
      }
      else {
        doExecute(executionEnvironment);
      }
    }
    finally {
      CvsEntriesManager.getInstance().unlockSynchronizationActions();
    }
  }

  @Override
  public void appendSelfCvsRootProvider(@NotNull Collection<CvsRootProvider> roots) throws CannotFindCvsRootException {
    roots.addAll(getAllCvsRoots());
  }

  private void doExecute(final CvsExecutionEnvironment executionEnvironment) throws VcsException {
    ReadWriteStatistics statistics = executionEnvironment.getReadWriteStatistics();
    Collection<CvsRootProvider> allCvsRoots;
    try {
      allCvsRoots = getAllCvsRoots();
    }
    catch (CannotFindCvsRootException e) {
      throw createVcsExceptionOn(e, null);
    }

    for (CvsRootProvider cvsRootProvider : allCvsRoots) {
      try {
        myLastProcessedCvsRoot = cvsRootProvider.getCvsRootAsString();
        execute(cvsRootProvider, executionEnvironment, statistics, executionEnvironment.getExecutor());
      }
      catch (IOCommandException e) {
        LOG.info(e);
        throw createVcsExceptionOn(e.getIOException(), cvsRootProvider.getCvsRootAsString());
      }
      catch (CommandException e) {
        LOG.info(e);
        Exception underlyingException = e.getUnderlyingException();
        if (underlyingException != null) {
          LOG.info(underlyingException);
        }
        throw createVcsExceptionOn(underlyingException == null ? e : underlyingException, cvsRootProvider.getCvsRootAsString());
      }
    }
  }

  private static VcsException createVcsExceptionOn(Exception e, String cvsRoot) {
    LOG.debug(e);
    String message = getMessageFrom(null, e);
    if (message == null) {
      return new CvsException(CvsBundle.message("exception.text.unknown.error"), e, cvsRoot);
    }
    return new CvsException(message, e, cvsRoot);
  }


  private static String getMessageFrom(String initialMessage, Throwable e) {
    if (e == null) return initialMessage;
    String result = initialMessage;
    if (result != null && result.length() > 0) return result;
    result = e.getLocalizedMessage();
    if (result == null || result.length() == 0) result = e.getMessage();
    return getMessageFrom(result, e.getCause());
  }

  protected abstract Collection<CvsRootProvider> getAllCvsRoots() throws CannotFindCvsRootException;

  protected void execute(CvsRootProvider root,
                         final CvsExecutionEnvironment executionEnvironment,
                         ReadWriteStatistics statistics, ModalityContext executor)
    throws CommandException,
           VcsException {
    IConnection connection = root.createConnection(statistics);
    execute(root, executionEnvironment, connection);

  }

  public void execute(final CvsRootProvider root,
                      final CvsExecutionEnvironment executionEnvironment,
                      IConnection connection) throws CommandException {
    Command command = createCommand(root, executionEnvironment);

    if (command == null) return;

    LOG.assertTrue(connection != null, root.getCvsRootAsString());

    final CvsMessagesListener cvsMessagesListener = executionEnvironment.getCvsMessagesListener();
    long start = System.currentTimeMillis();

    try {
      final IClientEnvironment clientEnvironment = createEnvironment(connection, root,
                                                                     myUpdatedFilesManager,
                                                                     executionEnvironment);

      myUpdatedFilesManager.setCvsFileSystem(clientEnvironment.getCvsFileSystem());
      final EventManager eventManager = new EventManager(CvsApplicationLevelConfiguration.getCharset());
      IGlobalOptions globalOptions = command.getIGlobalOptions();


      final IRequestProcessor requestProcessor = new RequestProcessor(clientEnvironment,
                                                                      globalOptions,
                                                                      eventManager,
                                                                      new StreamLogger(),
                                                                      executionEnvironment.getCvsCommandStopper(),
                                                                      PServerCvsSettings.getTimeoutMillis());

      eventManager.addFileInfoListener(this);
      eventManager.addEntryListener(this);
      eventManager.addMessageListener(this);
      eventManager.addModuleExpansionListener(this);


      CvsMessagesTranslator cvsMessagesTranslator = new CvsMessagesTranslator(cvsMessagesListener,
                                                                              clientEnvironment.getCvsFileSystem(),
                                                                              myUpdatedFilesManager,
                                                                              root.getCvsRootAsString());
      cvsMessagesTranslator.registerTo(eventManager);
      final CvsEntriesManager cvsEntriesManager = CvsEntriesManager.getInstance();
      if (shouldMakeChangesOnTheLocalFileSystem()) {
        eventManager.addEntryListener(new MergeSupportingEntryListener(clientEnvironment, cvsEntriesManager, myUpdatedFilesManager));
        eventManager.addMessageListener(myUpdatedFilesManager);
      }


      modifyOptions(command.getGlobalOptions());
      String commandString = composeCommandString(root, command);
      cvsMessagesListener.commandStarted(commandString);
      setProgressText(CvsBundle.message("progress.text.command.running.for.file", getOperationName(), root.getCvsRootAsString()));
      try {
        command.execute(requestProcessor, eventManager, eventManager, clientEnvironment, new IProgressViewer() {
          public void setProgress(double value) {
          }
        });
      }
      catch (AuthenticationException e) {
        if (! root.isOffline()) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
              final LoginPerformer.MyForRootProvider performer =
                new LoginPerformer.MyForRootProvider(Collections.singletonList(root), new Consumer<VcsException>() {
                  public void consume(VcsException e) {
                    LOG.info(e);
                  }
                });
              // for pserver to check password matches connection
              performer.setForceCheck(true);                       
              performer.loginAll(ModalityContextImpl.NON_MODAL);
            }
          });
          return;
        }
        throw root.processException(new CommandException(e, "Authentication problem"));
      }
      cvsMessagesTranslator.operationCompleted();
    }
    catch (CommandException t) {
      throw root.processException(t);
    }
    finally {
      cvsMessagesListener.commandFinished(composeCommandString(root, command),
                                          System.currentTimeMillis() - start);
      executeFinishActions();
    }
  }

  @NonNls protected abstract String getOperationName();

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String composeCommandString(CvsRootProvider root, Command command) {
    StringBuffer result = new StringBuffer();
    result.append(root.getLocalRoot());
    result.append(" cvs ");
    GlobalOptions globalOptions = command.getGlobalOptions();
    if (globalOptions.isCheckedOutFilesReadOnly()) result.append("-r ");
    if (globalOptions.isDoNoChanges()) result.append("-n ");
    if (globalOptions.isNoHistoryLogging()) result.append("-l ");
    if (globalOptions.isSomeQuiet()) result.append("-q ");
    result.append(command.getCvsCommandLine());
    return result.toString();
  }

  protected boolean shouldMakeChangesOnTheLocalFileSystem() {
    return true;
  }

  protected boolean runInExclusiveLock() {
    return true;
  }

  private static void setProgressText(String text) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) progressIndicator.setText(text);
  }

  private IClientEnvironment createEnvironment(IConnection connection,
                                               final CvsRootProvider root,
                                               UpdatedFilesManager mergedFilesCollector,
                                               CvsExecutionEnvironment cvsExecutionEnv) {
    File localRoot = getLocalRootFor(root);
    File adminRoot = getAdminRootFor(root);

    LOG.assertTrue(localRoot != null, getClass().getName());
    LOG.assertTrue(adminRoot != null, getClass().getName());

    return new ClientEnvironment(connection,
                                 localRoot, adminRoot, root.getCvsRoot(),
                                 createLocalFileReader(),
                                 createLocalFileWriter(root.getCvsRootAsString(),
                                                       mergedFilesCollector,
                                                       cvsExecutionEnv),
                                 myAdminReader, myAdminWriter,
                                 getIgnoreFileFilter(), new FileReadOnlyHandler(),
                                 CvsApplicationLevelConfiguration.getCharset());
  }

  protected ILocalFileWriter createLocalFileWriter(String cvsRoot,
                                                   UpdatedFilesManager mergedFilesCollector,
                                                   CvsExecutionEnvironment cvsExecutionEnvironment) {
    return new StoringLineSeparatorsLocalFileWriter(new ReceiveTextFilePreprocessor(createReceivedFileProcessor(mergedFilesCollector,
                                                                                                                cvsExecutionEnvironment.getPostCvsActivity())),
                                                    cvsExecutionEnvironment.getErrorProcessor(),
                                                    myUpdatedFilesManager,
                                                    cvsRoot,
                                                    this);
  }

  protected ReceivedFileProcessor createReceivedFileProcessor(UpdatedFilesManager mergedFilesCollector, PostCvsActivity postCvsActivity) {
    return ReceivedFileProcessor.DEFAULT;
  }

  protected ILocalFileReader createLocalFileReader() {
    return new LocalFileReaderBasedOnVFS(new SendTextFilePreprocessor(), this);
  }

  protected IIgnoreFileFilter getIgnoreFileFilter() {
    return new IgnoreFileFilterBasedOnCvsEntriesManager();
  }

  protected File getAdminRootFor(CvsRootProvider root) {
    return root.getAdminRoot();
  }

  protected File getLocalRootFor(CvsRootProvider root) {
    return root.getLocalRoot();
  }

  public void fileInfoGenerated(Object info) {
    if (info instanceof UpdateFileInfo) {
      UpdateFileInfo updateFileInfo = ((UpdateFileInfo)info);
      if (FileMessage.CONFLICT.equals(updateFileInfo.getType())) {
        CvsUtil.addConflict(updateFileInfo.getFile());
      }
    }
  }

  public void gotEntry(FileObject fileObject, Entry entry) {
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
  }

  public void moduleExpanded(String module) {

  }

  public boolean fileIsUnderProject(final VirtualFile file) {
    return true;
  }

  public boolean fileIsUnderProject(File file) {
    return true;
  }

  public String getLastProcessedCvsRoot() {
    return myLastProcessedCvsRoot;
  }

  private static class MergeSupportingEntryListener implements IEntryListener {
    private final IClientEnvironment myClientEnvironment;
    private final CvsEntriesManager myCvsEntriesManager;
    private final UpdatedFilesManager myUpdatedFilesManager;
    private final Map<File, Entry> myFileToPreviousEntryMap = new HashMap<File, Entry>();

    public MergeSupportingEntryListener(IClientEnvironment clientEnvironment,
                                        CvsEntriesManager cvsEntriesManager,
                                        UpdatedFilesManager mergedFilesCollector) {
      myClientEnvironment = clientEnvironment;
      myCvsEntriesManager = cvsEntriesManager;
      myUpdatedFilesManager = mergedFilesCollector;
    }

    public void gotEntry(FileObject fileObject, Entry entry) {
      IFileSystem localFileSystem = myClientEnvironment.getCvsFileSystem().getLocalFileSystem();
      File file = localFileSystem.getFile(fileObject);
      if (myUpdatedFilesManager.fileIsNotUpdated(file)) {
        return;
      }
      File parent = file.getParentFile();
      VirtualFile virtualParent = CvsVfsUtil.findFileByIoFile(parent);
      if (entry != null) {
        Entry previousEntry = myFileToPreviousEntryMap.containsKey(file)
                              ?
                              myFileToPreviousEntryMap.get(file)
                              : CvsEntriesManager.getInstance().getCachedEntry(virtualParent, entry.getFileName());
        if (previousEntry != null) {
          myFileToPreviousEntryMap.put(file, previousEntry);
          if (entry.isResultOfMerge()) {
            final UpdatedFilesManager.CurrentMergedFileInfo info = myUpdatedFilesManager.getInfo(file);
            info.registerNewRevision(previousEntry);
            CvsUtil.saveRevisionForMergedFile(virtualParent,
                                              info.getOriginalEntry(),
                                              info.getRevisions());
          }
        }
      }
      else {
        myCvsEntriesManager.removeEntryForFile(parent, fileObject.getName());
      }
      if (entry != null) {
        myCvsEntriesManager.setEntryForFile(virtualParent, entry);
      }

      if (entry == null || !entry.isResultOfMerge()) {
        CvsUtil.removeConflict(file);
      }
    }
  }

  public void binaryMessageSent(final byte[] bytes) {
  }
}
