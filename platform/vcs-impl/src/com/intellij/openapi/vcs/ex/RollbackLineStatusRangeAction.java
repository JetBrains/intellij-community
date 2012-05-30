/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;

/**
* @author irengrig
*/
public class RollbackLineStatusRangeAction extends BaseLineStatusRangeAction {
  public RollbackLineStatusRangeAction(final LineStatusTracker lineStatusTracker, final Range range, final Editor editor) {
    super(VcsBundle.message("action.name.rollback"), AllIcons.Actions.Reset, lineStatusTracker, range);
  }

  public boolean isEnabled() {
    return true;
  }

  public void actionPerformed(final AnActionEvent e) {
    CommandProcessor.getInstance().executeCommand(myLineStatusTracker.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (!myLineStatusTracker.getDocument().isWritable()) {
              final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler
                .getInstance(myLineStatusTracker.getProject()).ensureFilesWritable(myLineStatusTracker.getVirtualFile());
              if (operationStatus.hasReadonlyFiles()) return;
            }
            myLineStatusTracker.rollbackChanges(myRange);
          }
        });
      }
    }, VcsBundle.message("command.name.rollback.change"), null);

  }
}
