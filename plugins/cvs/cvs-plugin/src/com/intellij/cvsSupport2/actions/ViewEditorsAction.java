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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditorsPanel;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;

import java.util.List;

/**
 * author: lesya
 */
public class ViewEditorsAction extends AbstractActionFromEditGroup {
  private EditorsOperation myEditorsOperation;

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.getViewEditorsOperationName();
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    myEditorsOperation = new EditorsOperation(context.getSelectedFiles());
    return new CommandCvsHandler(CvsBundle.getViewEditorsOperationName(), myEditorsOperation);
  }

  @Override
  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully){
      List<EditorInfo> editors = myEditorsOperation.getEditors();
      String filePath = CvsVfsUtil.getFileFor(context.getSelectedFile()).getAbsolutePath();
      final Project project = context.getProject();
      if (project == null) {
        return;
      }
      if (editors.isEmpty()){
        VcsBalloonProblemNotifier.showOverChangesView(project,
                                                      CvsBundle.message("message.error.no.editors.for.file", filePath),
                                                      MessageType.INFO);
      } else {
        tabbedWindow.addTab(CvsBundle.message("message.editors.for.file", filePath),
                            new EditorsPanel(project, editors), true, true, true, true, null, "cvs.editors");
      }
    }
  }
}
