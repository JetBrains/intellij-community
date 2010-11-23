/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author irengrig
 */
public class SelectRepositoryAndShowLogAction extends AnAction {
  public SelectRepositoryAndShowLogAction() {
    super("Select and browse git repo");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final VirtualFile[] virtualFiles = FileChooser.chooseFiles(project, new FileChooserDescriptor(false, true, false, true, false, false));
    if (virtualFiles == null || virtualFiles.length == 0) return;

    new MyDialog(project, virtualFiles).show();
  }

  private static class MyDialog extends DialogWrapper {
    private GitLog myGitLog;

    private MyDialog(Project project, final VirtualFile[] virtualFiles) {
      super(project, true);
      myGitLog = LogFactoryService.getInstance(project).createComponent();
      myGitLog.rootsChanged(Arrays.asList(virtualFiles));
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myGitLog.getVisualComponent();
    }

    @Override
    public void doCancelAction() {
      // todo stop listener?
      super.doCancelAction();
    }

    @Override
    protected void doOKAction() {
      // todo stop listener?
      super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
     // myGitLogLongPanel.setModalityState(ModalityState.current());
      return super.getPreferredFocusedComponent();
    }
  }
}
