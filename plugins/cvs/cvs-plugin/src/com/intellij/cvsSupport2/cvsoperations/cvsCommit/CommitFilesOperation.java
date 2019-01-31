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
package com.intellij.cvsSupport2.cvsoperations.cvsCommit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.commit.CommitCommand;

public class CommitFilesOperation extends CvsOperationOnFiles {
  private Object myMessage;
  private final boolean myMakeNewFilesReadOnly;

  public CommitFilesOperation(String message, boolean makeNewFilesReadOnly) {
    myMessage = message;
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
  }

  public CommitFilesOperation(boolean makeNewFilesReadOnly) {
    this(null, makeNewFilesReadOnly);
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final CommitCommand result = new CommitCommand();
    addFilesToCommand(root, result);
    if(myMessage != null)
      result.setMessage(myMessage.toString());
    return result;
  }

  public void setMessage(Object parameters) {
    myMessage = parameters;
  }

  @Override
  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setCheckedOutFilesReadOnly(myMakeNewFilesReadOnly);
  }

  @Override
  protected String getOperationName() {
    return "commit";
  }

  @Override
  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  @Override
  public boolean runInReadThread() {
    return false;
  }
}
