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