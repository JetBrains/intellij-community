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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorMessagesProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsCompositeListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;

import java.util.ArrayList;
import java.util.List;

public abstract class CvsHandler extends CvsMessagesAdapter{

  @NonNls private static final String NULL_HANDLER_NAME = "Null";
  public static final CvsHandler NULL = new CvsHandler(NULL_HANDLER_NAME, FileSetToBeUpdated.EMPTY) {

    @Override
    protected int getFilesToProcessCount() {
      return 0;
    }

    @Override
    public boolean isCanceled() {
      return true;
    }

    @Override
    public boolean login(Project project) {
      return false;
    }
  };

  public static final int UNKNOWN_COUNT = -1;

  protected final List<VcsException> myErrors = new ArrayList<>();
  protected final CvsCompositeListener myCompositeListener = new CvsCompositeListener();
  protected ErrorMessagesProcessor myErrorMessageProcessor = new ErrorMessagesProcessor(myErrors);
  private int myFilesToProcess = -1;
  private int myProcessedFiles = 0;
  private final CvsMessagesConsole myMessagesConsole;
  private final String myTitle;
  protected CvsListenerWithProgress myProgressListener;
  private final FileSetToBeUpdated myFiles;

  public CvsHandler(String title, FileSetToBeUpdated files) {
    myTitle = title;
    myMessagesConsole = new CvsMessagesConsole();
    myFiles = files;
  }

  public void internalRun(Project project, ModalityContext executor, final boolean runInReadAction) {
  }

  public void addCvsListener(CvsMessagesListener listener) {
    myCompositeListener.addCvsListener(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myCompositeListener.removeCvsListener(listener);
  }

  public String getTitle() {
    return myTitle;
  }

  public List<VcsException> getErrors() {
    return myErrorMessageProcessor.getErrors();
  }

  public List<VcsException> getErrorsExceptAborted() {
    final List<VcsException> result = new ArrayList<>();
    for(VcsException ex: myErrorMessageProcessor.getErrors()) {
      if (!(ex.getCause() instanceof CommandAbortedException)) {
        result.add(ex);
      }
    }
    return result;
  }

  @Override
  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    setText2(message);
    incProgress();
  }

  @Override
  public void addMessage(String message) {
    setText2(message);
  }

  private void setText2(String message) {
    if (getProgress() != null) getProgress().setText2(message);
  }

  protected void incProgress() {
    if (getProgress() == null) return;
    myProcessedFiles++;

    if (myFilesToProcess == UNKNOWN_COUNT) {
      myFilesToProcess = getFilesToProcessCount();
    }

    if (myFilesToProcess != UNKNOWN_COUNT) {
      getProgress().setFraction((double)myProcessedFiles / (double)myFilesToProcess);
    }
  }

  protected abstract int getFilesToProcessCount();

  public void connectToOutputView(@NotNull Editor editor, Project project) {
    myMessagesConsole.connectToOutputView(editor, project);
  }

  protected boolean runInReadThread(){
    return true;
  }

  public void run(Project project, final ModalityContext executor) {
    initializeListeners();
    try {
      internalRun(project, executor, runInReadThread());
    } finally {
      cleanupListeners();
    }
    if (isCanceled())  throw new ProcessCanceledException();
    final ProgressIndicator progress = getProgress();
    if (progress != null) {
      if (progress.isCanceled()) throw new ProcessCanceledException();
    }
  }

  private void cleanupListeners() {
    removeCvsListener(getProgressListener());
    removeCvsListener(myMessagesConsole);
    removeCvsListener(this);
    removeCvsListener(myErrorMessageProcessor);
  }

  protected CvsListenerWithProgress getProgressListener() {
    if (myProgressListener == null) {
      myProgressListener = CvsListenerWithProgress.createOnProgress();
    }
    return myProgressListener;
  }

  private void initializeListeners() {
    addCvsListener(getProgressListener());
    addCvsListener(myMessagesConsole);
    addCvsListener(this);
    addCvsListener(myErrorMessageProcessor);
  }

  protected ProgressIndicator getProgress() {
    return getProgressListener().getProgressIndicator();
  }

  public void finish() {}

  public void beforeLogin() {}

  public abstract boolean login(Project project);

  public FileSetToBeUpdated getFiles() {
    return myFiles;
  }

  public boolean canBeCanceled(){
    return true;
  }

  public abstract boolean isCanceled();

  public PerformInBackgroundOption getBackgroundOption(Project project) {
    return null;
  }
}
