package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class CvsCompositeListener implements CvsListenersCollection, CvsMessagesListener {
  private final Collection myListeners = new ArrayList();

  public void addCvsListener(CvsMessagesListener listener) {
    myListeners.add(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myListeners.remove(listener);
  }

  public void commandFinished(String commandName, long time) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).commandFinished(commandName, time);
    }
  }

  public void addFileMessage(FileMessage message) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).addFileMessage(message);
    }

  }

  public void addMessage(MessageEvent event) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).addMessage(event);
    }
  }

  public void commandStarted(String command) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).commandStarted(command);
    }
  }

  public void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).addError(message, relativeFilePath, cvsFileSystem, cvsRoot);
    }

  }

  public void addWarning(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).addWarning(message, relativeFilePath, cvsFileSystem, cvsRoot);
    }

  }

  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      ((CvsMessagesListener) iterator.next()).addFileMessage(message, cvsFileSystem);
    }

  }

}