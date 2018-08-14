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
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchCommand;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchOperation extends CvsOperationOnFiles{
  private final WatchMode myWatchMode;
  private final Watch myWatch;

  public WatchOperation(WatchMode watchMode) {
    this(watchMode, Watch.ALL);
  }

  public WatchOperation(WatchMode watchMode, Watch watch) {
    myWatchMode = watchMode;
    myWatch = watch;
  }

  @Override
  protected String getOperationName() {
    return "watch";
  }

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    WatchCommand result = new WatchCommand();
    result.setWatchMode(myWatchMode);
    result.setWatch(myWatch);
    addFilesToCommand(root, result);
    return result;
  }
}