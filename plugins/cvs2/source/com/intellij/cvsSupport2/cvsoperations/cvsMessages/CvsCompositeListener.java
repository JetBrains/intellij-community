package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.ArrayList;
import java.util.Collection;

public class CvsCompositeListener implements CvsListenersCollection, CvsMessagesListener {
  private final Collection<CvsMessagesListener> myListeners = new ArrayList<CvsMessagesListener>();

  public void addCvsListener(CvsMessagesListener listener) {
    myListeners.add(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myListeners.remove(listener);
  }

  public void commandFinished(String commandName, long time) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).commandFinished(commandName, time);
    }
  }

  public void addFileMessage(FileMessage message) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addFileMessage(message);
    }

  }

  public void addMessage(String message) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addMessage(message);
    }
  }

  public void addMessage(MessageEvent event) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addMessage(event);
    }
  }

  public void commandStarted(String command) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).commandStarted(command);
    }
  }

  public void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addError(message, relativeFilePath, cvsFileSystem, cvsRoot);
    }

  }

  public void addWarning(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addWarning(message, relativeFilePath, cvsFileSystem, cvsRoot);
    }

  }

  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addFileMessage(message, cvsFileSystem);
    }

  }

}