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
package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.util.containers.ContainerUtil;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.Collection;

public class CvsCompositeListener implements CvsListenersCollection, CvsMessagesListener {
  private final Collection<CvsMessagesListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void addCvsListener(CvsMessagesListener listener) {
    myListeners.add(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void commandFinished(String commandName, long time) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).commandFinished(commandName, time);
    }
  }

  @Override
  public void addFileMessage(FileMessage message) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addFileMessage(message);
    }
  }

  @Override
  public void addMessage(String message) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addMessage(message);
    }
  }

  @Override
  public void addMessage(MessageEvent event) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addMessage(event);
    }
  }

  @Override
  public void commandStarted(String command) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).commandStarted(command);
    }
  }

  @Override
  public void addError(String message, String relativeFilePath, ICvsFileSystem cvsFileSystem, String cvsRoot, boolean warning) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addError(message, relativeFilePath, cvsFileSystem, cvsRoot, warning);
    }
  }

  @Override
  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    for (final Object myListener : myListeners) {
      ((CvsMessagesListener)myListener).addFileMessage(message, cvsFileSystem);
    }
  }
}