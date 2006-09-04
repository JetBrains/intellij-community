package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorMessagesProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.ArrayList;
import java.util.List;

public abstract class CvsHandler extends CvsMessagesAdapter{

  @NonNls private static final String NULL_HANDLER_NAME = "Null";
  public static CvsHandler NULL = new CvsHandler(NULL_HANDLER_NAME, FileSetToBeUpdated.EMPTY) {
    protected void addCvsListener(CvsMessagesListener listener) {

    }

    protected void removeCvsListener(CvsMessagesListener listener) {

    }

    protected int getFilesToProcessCount() {
      return 0;
    }

    public boolean isCanceled() {
      return true;
    }

    public boolean login(ModalityContext executor) throws Exception {
      return false;
    }
  };

  public static final int UNKNOWN_COUNT = -1;

  protected final List<VcsException> myErrors = new ArrayList<VcsException>();
  protected ErrorMessagesProcessor myErrorMessageProcessor = new ErrorMessagesProcessor(myErrors);
  private int myFilesToProcess = -1;
  private int myProcessedFiles = 0;
  private CvsMessagesConsole myMessagesConsole;
  private final String myTitle;
  protected CvsListenerWithProgress myProgressListener;
  private final FileSetToBeUpdated myFiles;

  public CvsHandler(String title, FileSetToBeUpdated files) {
    myTitle = title;
    myMessagesConsole = new CvsMessagesConsole();
    myFiles = files;
  }

  public void internalRun(ModalityContext executor, final boolean runInReadAction) {
  }

  protected abstract void addCvsListener(CvsMessagesListener listener);

  protected abstract void removeCvsListener(CvsMessagesListener listener);

  public String getTitle() {
    return myTitle;
  }

  public List<VcsException> getErrors() {
    return myErrorMessageProcessor.getErrors();
  }

  public List<VcsException> getWarnings() {
    return myErrorMessageProcessor.getWarnings();
  }

  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    setText2(message);
    incProgress();
  }

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

  public void connectToOutputView(Editor editor, Project project) {
    myMessagesConsole.connectToOutputView(editor, project);
  }

  protected boolean runInReadThread(){
    return true;
  }

  public void run(final ModalityContext executor) {
    initializeListeners();
    try {
      internalRun(executor, runInReadThread());
    } finally {
      deinitializeListeners();
    }
    if (isCanceled())  throw new ProcessCanceledException();
    ProgressIndicator progress = getProgress();
    if (progress != null) {
      if (progress.isCanceled()) throw new ProcessCanceledException();
    }
  }

  private void deinitializeListeners() {
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

  public void runComplitingActivities() {
  }

  public void beforeLogin(){

  }

  public abstract boolean login(ModalityContext executor) throws Exception;

  public FileSetToBeUpdated getFiles() {
    return myFiles;
  }

  public boolean canBeCanceled(){
    return true;
  }

  public abstract boolean isCanceled();

  public String getCancelButtonText() {
    return null;
  }

}
