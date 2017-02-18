/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.connections.pserver.PServerCvsSettings;
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
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Collection;
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

  @Override
  public void execute(CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException, CommandAbortedException {
    if (runInExclusiveLock()) {
      synchronized (CvsOperation.class) {
        doExecute(executionEnvironment, underReadAction);
      }
    }
    else {
      doExecute(executionEnvironment, underReadAction);
    }
  }

  @Override
  public void appendSelfCvsRootProvider(@NotNull Collection<CvsEnvironment> roots) throws CannotFindCvsRootException {
    roots.addAll(getAllCvsRoots());
  }

  private void doExecute(final CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException {
    final VcsException[] exc = new VcsException[1];
    final Runnable action = () -> {
      try {
        final ReadWriteStatistics statistics = executionEnvironment.getReadWriteStatistics();
        final Collection<CvsRootProvider> allCvsRoots;
        try {
          allCvsRoots = getAllCvsRoots();
        }
        catch (CannotFindCvsRootException e) {
          throw createVcsExceptionOn(e, null);
        }

        final IProgressViewer progressViewer = new IProgressViewer() {

          @Override
          public void setProgress(double value) {
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progressIndicator != null) progressIndicator.setFraction(value);
          }
        };
        int count = 0;
        final double step = 1.0 / allCvsRoots.size();
        for (CvsRootProvider cvsRootProvider : allCvsRoots) {
          try {
            final double lowerBound = step * count;
            final RangeProgressViewer partialProgress = new RangeProgressViewer(progressViewer, lowerBound, lowerBound + step);
            myLastProcessedCvsRoot = cvsRootProvider.getCvsRootAsString();
            execute(cvsRootProvider, executionEnvironment, statistics, partialProgress);
            count++;
          }
          catch (IOCommandException e) {
            LOG.info(e);
            throw createVcsExceptionOn(e.getIOException(), cvsRootProvider.getCvsRootAsString());
          }
          catch (CommandException e) {
            LOG.info(e);
            final Exception underlyingException = e.getUnderlyingException();
            if (underlyingException != null) {
              LOG.info(underlyingException);
            }
            throw createVcsExceptionOn(underlyingException == null ? e : underlyingException, cvsRootProvider.getCvsRootAsString());
          }
        }
      }
      catch (VcsException e) {
        exc[0] = e;
      }
    };
    if (underReadAction) {
      ApplicationManager.getApplication().runReadAction(action);
    } else {
      action.run();
    }
    if (exc[0] != null) throw exc[0];
  }

  private static VcsException createVcsExceptionOn(Exception e, String cvsRoot) {
    LOG.debug(e);
    final String message = getMessageFrom(null, e);
    if (message == null) {
      return new CvsException(CvsBundle.message("exception.text.unknown.error"), e, cvsRoot);
    }
    return new CvsException(message, e, cvsRoot);
  }


  private static String getMessageFrom(String initialMessage, Throwable e) {
    if (e == null) return initialMessage;
    String result = initialMessage;
    if (result != null && !result.isEmpty()) return result;
    result = e.getLocalizedMessage();
    if (result == null || result.isEmpty()) result = e.getMessage();
    return getMessageFrom(result, e.getCause());
  }

  protected abstract Collection<CvsRootProvider> getAllCvsRoots() throws CannotFindCvsRootException;

  protected void execute(CvsRootProvider root,
                         final CvsExecutionEnvironment executionEnvironment,
                         ReadWriteStatistics statistics, IProgressViewer progressViewer)
    throws CommandException, VcsException {
    final IConnection connection = root.createConnection(statistics);
    execute(root, executionEnvironment, connection, progressViewer);
  }

  public void execute(final CvsRootProvider root,
                      final CvsExecutionEnvironment executionEnvironment,
                      IConnection connection, IProgressViewer progressViewer) throws CommandException {
    final Command command = createCommand(root, executionEnvironment);

    if (command == null) return;

    LOG.assertTrue(connection != null, root.getCvsRootAsString());

    final CvsMessagesListener cvsMessagesListener = executionEnvironment.getCvsMessagesListener();
    final long start = System.currentTimeMillis();

    try {
      final IClientEnvironment clientEnvironment = createEnvironment(connection, root,
                                                                     myUpdatedFilesManager,
                                                                     executionEnvironment);

      myUpdatedFilesManager.setCvsFileSystem(clientEnvironment.getCvsFileSystem());
      final EventManager eventManager = new EventManager(CvsApplicationLevelConfiguration.getCharset());
      final IGlobalOptions globalOptions = command.getGlobalOptions();


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


      final CvsMessagesTranslator cvsMessagesTranslator = new CvsMessagesTranslator(cvsMessagesListener,
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
      final String commandString = composeCommandString(root, command);
      cvsMessagesListener.commandStarted(commandString);
      setProgressText(CvsBundle.message("progress.text.command.running.for.file", getOperationName(), root.getCvsRootAsString()));
      try {
        command.execute(requestProcessor, eventManager, eventManager, clientEnvironment, progressViewer);
      }
      catch (AuthenticationException e) {
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
    final StringBuilder result = new StringBuilder();
    result.append(root.getLocalRoot());
    result.append(" cvs ");
    final GlobalOptions globalOptions = command.getGlobalOptions();
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
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) progressIndicator.setText(text);
  }

  private IClientEnvironment createEnvironment(IConnection connection,
                                               final CvsRootProvider root,
                                               UpdatedFilesManager mergedFilesCollector,
                                               CvsExecutionEnvironment cvsExecutionEnv) {
    final File localRoot = getLocalRootFor(root);
    final File adminRoot = getAdminRootFor(root);

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

  @Override
  public void fileInfoGenerated(Object info) {
    if (info instanceof UpdateFileInfo) {
      final UpdateFileInfo updateFileInfo = ((UpdateFileInfo)info);
      if (FileMessage.CONFLICT.equals(updateFileInfo.getType())) {
        CvsUtil.addConflict(updateFileInfo.getFile());
      }
    }
  }

  @Override
  public void gotEntry(FileObject fileObject, Entry entry) {
  }

  @Override
  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
  }

  @Override
  public void moduleExpanded(String module) {

  }

  @Override
  public boolean fileIsUnderProject(final VirtualFile file) {
    return true;
  }

  @Override
  public boolean fileIsUnderProject(File file) {
    return true;
  }

  @Override
  public String getLastProcessedCvsRoot() {
    return myLastProcessedCvsRoot;
  }

  private static class MergeSupportingEntryListener implements IEntryListener {
    private final IClientEnvironment myClientEnvironment;
    private final CvsEntriesManager myCvsEntriesManager;
    private final UpdatedFilesManager myUpdatedFilesManager;
    private final Map<File, Entry> myFileToPreviousEntryMap = new HashMap<>();

    public MergeSupportingEntryListener(IClientEnvironment clientEnvironment,
                                        CvsEntriesManager cvsEntriesManager,
                                        UpdatedFilesManager mergedFilesCollector) {
      myClientEnvironment = clientEnvironment;
      myCvsEntriesManager = cvsEntriesManager;
      myUpdatedFilesManager = mergedFilesCollector;
    }

    @Override
    public void gotEntry(FileObject fileObject, Entry entry) {
      final IFileSystem localFileSystem = myClientEnvironment.getCvsFileSystem().getLocalFileSystem();
      final File file = localFileSystem.getFile(fileObject);
      if (myUpdatedFilesManager.fileIsNotUpdated(file)) {
        return;
      }
      final File parent = file.getParentFile();
      final VirtualFile virtualParent = CvsVfsUtil.findFileByIoFile(parent);
      if (entry != null) {
        final Entry previousEntry = myFileToPreviousEntryMap.containsKey(file)
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

  @Override
  public void binaryMessageSent(final byte[] bytes) {
  }
}
