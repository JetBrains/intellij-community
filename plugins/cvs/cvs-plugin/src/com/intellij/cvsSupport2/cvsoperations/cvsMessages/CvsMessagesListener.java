package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.text.MessageFormat;

public interface CvsMessagesListener {

  CvsMessagesListener STANDARD_OUTPUT = new CvsMessagesAdapter() {
    public void addMessage(MessageEvent event) {
      if (event.getMessage().length() > 0)
        System.out.println(event.getMessage());
      System.out.flush();
    }

    public void commandStarted(String command) {
      System.out.println(com.intellij.CvsBundle.message("output.command.started", command));
      System.out.flush();
    }

  };

  void addMessage(MessageEvent event);

  void commandFinished(String commandName, long time);

  void addFileMessage(FileMessage message);

  void commandStarted(String command);

  void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot);

  void addWarning(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot);

  void addFileMessage(String message, ICvsFileSystem cvsFileSystem);

  void addMessage(String message);
}