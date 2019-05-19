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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * author: lesya
 */
public class RestoreFileAction extends ActionOnSelectedElement {

  protected final VirtualFile myParent;
  private final String myFileName;

  public RestoreFileAction(VirtualFile parent, String fileName) {
    super(true);
    getVisibility().shouldNotBePerformedOnDirectory();
    myParent = parent;
    myFileName = fileName;
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.restore.file");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    return CommandCvsHandler.createRestoreFileHandler(
      myParent, myFileName, CvsConfiguration.getInstance(context.getProject()).MAKE_NEW_FILES_READONLY);
  }

  @Override
  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    final List<VcsException> errors = handler.getErrors();
    if (errors == null || errors.isEmpty()) {
      CvsEntriesManager.getInstance().clearCachedEntriesFor(myParent);
    }
  }
}
