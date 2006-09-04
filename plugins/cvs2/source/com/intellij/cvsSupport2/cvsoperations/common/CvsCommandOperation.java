/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.ClientEnvironment;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.RequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class CvsCommandOperation extends CvsOperation implements IFileInfoListener,
                                                                          IMessageListener,
                                                                          IEntryListener,
                                                                          IModuleExpansionListener,
                                                                          ProjectContentInfoProvider {

  private static IAdminReader DEFAULT_ADMIN_READER = new AdminReaderOnCache();

  protected final IAdminReader myAdminReader;
  protected final IAdminWriter myAdminWriter;

  protected static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation");
  private boolean myLoggedIn = false;
  private String myLastProcessedCvsRoot;
  private final UpdatedFilesManager myUpdatedFilesManager = new UpdatedFilesManager();

  public CvsCommandOperation() {
    this(DEFAULT_ADMIN_READER);
  }

  public CvsCommandOperation(IAdminReader adminReader, IAdminWriter adminWriter) {
    myAdminReader = adminReader;
    myAdminWriter = adminWriter;
  }

  public CvsCommandOperation(IAdminReader adminReader) {
    myAdminReader = adminReader;
    myAdminWriter = new AdminWriterOnCache(myUpdatedFilesManager, this);
  }

  public CvsCommandOperation(IAdminWriter adminWriter) {
    this(DEFAULT_ADMIN_READER, adminWriter);
  }

  abstract protected Command createCommand(CvsRootProvider root,
                                           CvsExecutionEnvironment cvsExecutionEnvironment);

  protected boolean login(Collection<CvsRootProvider> processedCvsRoots, ModalityContext executor)
    throws CannotFindCvsRootException {
    setIsLoggedIn();
    Collection allCvsRoots = getAllCvsRoots();

    for (Iterator each = allCvsRoots.iterator(); each.hasNext();) {
      CvsRootProvider cvsRootProvider = (CvsRootProvider)each.next();
      if (processedCvsRoots.contains(cvsRootProvider)) continue;
      processedCvsRoots.add(cvsRootProvider);
      if (!cvsRootProvider.login(executor)) return false;
    }
    return true;

  }

  public void setIsLoggedIn() {
    myLoggedIn = true;
  }

  public void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException,
                                                                           CommandAbortedException {
    LOG.assertTrue(myLoggedIn);
    ReadWriteStatistics statistics = executionEnvironment.getReadWriteStatistics();
    CvsEntriesManager.getInstance().lockSynchronizationActions();
    try {
      synchronized (CvsOperation.class) {

        Collection allCvsRoots;
        try {
          allCvsRoots = getAllCvsRoots();
        }
        catch (CannotFindCvsRootException e) {
          throw createVcsExceptionOn(e, null);
        }

        for (Iterator each = allCvsRoots.iterator(); each.hasNext();) {
          CvsRootProvider cvsRootProvider = (CvsRootProvider)each.next();
          try {
            myLastProcessedCvsRoot = cvsRootProvider.getCvsRootAsString();
            execute(cvsRootProvider, executionEnvironment, statistics,
                    executionEnvironment.getExecutor());
          }
          catch (IOCommandException e) {
            LOG.info(e);
            throw createVcsExceptionOn(e.getIOException(), cvsRootProvider.getCvsRootAsString());
          }
          catch (CommandException e) {
            LOG.info(e);
            Exception underlyingException = e.getUnderlyingException();
            LOG.info(underlyingException);
            throw createVcsExceptionOn(underlyingException == null ? e : underlyingException,
                                       cvsRootProvider.getCvsRootAsString());
          }
        }
      }
    }
    finally {
      CvsEntriesManager.getInstance().unlockSynchronizationActions();
    }
  }

  private VcsException createVcsExceptionOn(Exception e, String cvsRoot) {
    LOG.debug(e);
    String message = getMessageFrom(null, e);
    if (message == null) {
      return new CvsException(com.intellij.CvsBundle.message("exception.text.unknown.error"), cvsRoot);
    }
    return new CvsException(message, cvsRoot);
  };


  String getMessageFrom(String initialMessage, Throwable e) {
    if (e == null) return initialMessage;
    String result = initialMessage;
    if (result != null && result.length() > 0) return result;
    result = e.getLocalizedMessage();
    if (result == null || result.length() == 0) result = e.getMessage();
    return getMessageFrom(result, e.getCause());
  }

  protected abstract Collection getAllCvsRoots() throws CannotFindCvsRootException;

  protected void execute(CvsRootProvider root,
                         final CvsExecutionEnvironment executionEnvironment,
                         ReadWriteStatistics statistics, ModalityContext executor)
    throws CommandException,
           CommandAbortedException,
           VcsException {
    IConnection connection = root.createConnection(statistics);
    execute(root, executionEnvironment, connection);

  }

  public void execute(CvsRootProvider root,
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
                                                                      executionEnvironment.getCvsCommandStopper());

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
      setProgressText(com.intellij.CvsBundle.message("progress.text.command.running.for.file", getOperationName(), root.getCvsRootAsString()));
      command.execute(requestProcessor, eventManager, eventManager, clientEnvironment, new IProgressViewer() {
        public void setProgress(double value) {
        }
      });
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
  private String composeCommandString(CvsRootProvider root, Command command) {
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

  private void setProgressText(String text) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) progressIndicator.setText(text);
  }

  private IClientEnvironment createEnvironment(IConnection connection,
                                               final CvsRootProvider root,
                                               UpdatedFilesManager mergedFilesCollector,
                                               CvsExecutionEnvironment cvsExecutionEnv) {
    File localRoot = getLocalRootFor(root);
    File adminRoot = getAdminRootFor(root);

    LOG.assertTrue(localRoot != null, this.getClass().getName());
    LOG.assertTrue(adminRoot != null, this.getClass().getName());

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
    return new StoringLineSeparatorsLocalFileWriter(new ReceiveTextFilePreprocessor(createRecievedFileProcessor(mergedFilesCollector,
                                                                                                                cvsExecutionEnvironment.getPostCvsActivity(),
                                                                                                                cvsExecutionEnvironment.getExecutor())),
                                                    cvsExecutionEnvironment.getErrorProcessor(),
                                                    myUpdatedFilesManager,
                                                    cvsRoot,
                                                    this);
  }

  protected ReceivedFileProcessor createRecievedFileProcessor(UpdatedFilesManager mergedFilesCollector,
                                                              PostCvsActivity postCvsActivity,
                                                              ModalityContext modalityContext) {
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

  public boolean fileIsUnderProject(VirtualFile file) {
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
