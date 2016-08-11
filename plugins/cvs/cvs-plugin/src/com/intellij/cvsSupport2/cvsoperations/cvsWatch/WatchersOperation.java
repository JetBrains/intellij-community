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
package com.intellij.cvsSupport2.cvsoperations.cvsWatch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.watchers.WatchersCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class WatchersOperation extends CvsOperationOnFiles{
  private final List<WatcherInfo> myWatchers = new ArrayList<>();
  public WatchersOperation(VirtualFile[] files){
    for (int i = 0; i < files.length; i++) {
      addFile(files[i]);
    }

  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    WatchersCommand result = new WatchersCommand();
    addFilesToCommand(root, result);
    return result;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    if (!error && !tagged){
      WatcherInfo info = WatcherInfo.createOn(message);
      if (info != null) myWatchers.add(info);
    }
  }

  protected String getOperationName() {
    return "watchers";
  }

  public List<WatcherInfo> getWatchers(){
    return myWatchers;
  }

}
