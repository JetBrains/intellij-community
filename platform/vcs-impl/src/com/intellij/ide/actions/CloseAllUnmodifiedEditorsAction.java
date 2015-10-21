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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CloseAllUnmodifiedEditorsAction extends CloseEditorsActionBase {

  protected boolean isFileToClose(final EditorComposite editor, final EditorWindow window) {
    return !window.getManager().isChanged(editor) && !window.isFilePinned(editor.getFile());
  }

  @Override
  protected boolean isActionEnabled(final Project project, final AnActionEvent event) {
    return super.isActionEnabled(project, event) && ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
  }

  protected String getPresentationText(final boolean inSplitter) {
    if (inSplitter) {
      return IdeBundle.message("action.close.all.unmodified.editors.in.tab.group");
    }
    else {
      return IdeBundle.message("action.close.all.unmodified.editors");
    }
  }
}
