package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

/**
 * author: lesya
 */
public class CvsMessagesAdapter implements CvsMessagesListener{
  public void addMessage(MessageEvent event) {
  }

  public void commandFinished(String commandName, long time) {
  }

  public void addFileMessage(FileMessage message) {
  }

  public void commandStarted(String command) {
  }

  public void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
  }

  public void addWarning(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
  }

  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
  }

  public void addMessage(String message) {
  }
}
