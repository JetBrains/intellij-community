package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

public class CvsListenerWithProgress extends CvsMessagesAdapter implements ICvsCommandStopper,ErrorRegistry {
  private final ProgressIndicator myProgressIndicator;
  private String myLastError;

  public CvsListenerWithProgress(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }

  public static CvsListenerWithProgress createOnProgress() {
    ProgressManager manager = ProgressManager.getInstance();
    return new CvsListenerWithProgress(manager.getProgressIndicator());
  }

  public void addFileMessage(FileMessage message) {
    if (myProgressIndicator != null) {
      message.showMessageIn(myProgressIndicator);
    }
  }

  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  public boolean isAborted() {
    if (myLastError != null) throw new CvsProcessException(myLastError);
    if (myProgressIndicator == null) return false;
    return myProgressIndicator.isCanceled();
  }

  public void registerError(String description) {
    myLastError = description;
  }
}
