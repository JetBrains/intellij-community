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
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatchOperation;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

public abstract class AbstractWatchOnOffAction extends AbstractActionFromEditGroup {
  @Override
  protected abstract String getTitle(VcsContext context);

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    WatchOperation watchOperation = new WatchOperation(getMode());
    VirtualFile[] files = context.getSelectedFiles();
    for (VirtualFile file : files) {
      watchOperation.addFile(file);
    }
    return new CommandCvsHandler(getTitle(context), watchOperation);
  }

  protected abstract WatchMode getMode();
}