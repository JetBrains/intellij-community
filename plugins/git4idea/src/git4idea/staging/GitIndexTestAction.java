/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.staging;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

public class GitIndexTestAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || virtualFile == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile);
    e.getPresentation().setEnabled(repository != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile);
    if (repository == null) return;

    FilePath filePath = VcsUtil.getFilePath(virtualFile);
    VirtualFile file = GitIndexManager.getInstance(project).getVirtualFile(repository, filePath);
    if (file == null) {
      Messages.showErrorDialog(project, "File not found", "Can't Create Index Virtual File");
      return;
    }

    new OpenFileDescriptor(project, file).navigate(true);
  }
}
