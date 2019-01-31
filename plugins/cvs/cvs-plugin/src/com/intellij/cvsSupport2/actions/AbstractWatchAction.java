/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatchOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui.WatcherDialog;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

public abstract class AbstractWatchAction extends AbstractActionFromEditGroup {
  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    CvsConfiguration configuration = CvsConfiguration.getInstance(context.getProject());
    WatcherDialog dialog = createDialog(configuration, context);
    if (!dialog.showAndGet()) {
      return CvsHandler.NULL;
    }
    Watch watch = dialog.getWatch();
    saveWatch(configuration, watch);
    WatchOperation watchOperation = new WatchOperation(getWatchOperation(), watch);
    VirtualFile[] files = context.getSelectedFiles();
    for (VirtualFile file : files) {
      watchOperation.addFile(file);
    }
    return new CommandCvsHandler(getTitle(context), watchOperation);
  }

  protected abstract WatcherDialog createDialog(CvsConfiguration configuration, VcsContext context);

  protected abstract WatchMode getWatchOperation();

  protected abstract void saveWatch(CvsConfiguration configuration, Watch watch);
}