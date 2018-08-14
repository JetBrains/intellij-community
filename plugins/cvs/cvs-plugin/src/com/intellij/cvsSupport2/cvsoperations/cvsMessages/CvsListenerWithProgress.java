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

import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

public class CvsListenerWithProgress extends CvsMessagesAdapter implements ICvsCommandStopper,ErrorRegistry {
  private ProgressIndicator myProgressIndicator;
  private String myLastError;
  private boolean myIndirectCancel;
  private boolean myPing;

  public CvsListenerWithProgress(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
    myIndirectCancel = false;
    myPing = false;
  }

  @Override
  public boolean isAlive() {
    return myPing;
  }

  @Override
  public void resetAlive() {
    myPing = false;
  }

  public static CvsListenerWithProgress createOnProgress() {
    return new CvsListenerWithProgress(ProgressManager.getInstance().getProgressIndicator());
  }

  @Override
  public void addFileMessage(FileMessage message) {
    if (myProgressIndicator != null) {
      message.showMessageIn(myProgressIndicator);
    }
  }

  public ProgressIndicator getProgressIndicator() {
    if (myProgressIndicator == null) {
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    }
    return myProgressIndicator;
  }

  public void indirectCancel() {
    myIndirectCancel = true;
  }

  @Override
  public boolean isAborted() {
    myPing = true;
    if (myLastError != null) throw new CvsProcessException(myLastError);
    if (myIndirectCancel) return true;
    final ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator == null) return false;
    return progressIndicator.isCanceled();
  }

  @Override
  public void registerError(String description) {
    myLastError = description;
  }
}
