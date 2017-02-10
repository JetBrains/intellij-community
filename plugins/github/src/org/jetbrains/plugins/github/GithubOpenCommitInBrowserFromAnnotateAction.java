/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubUtil;

public class GithubOpenCommitInBrowserFromAnnotateAction extends GithubOpenInBrowserAction implements UpToDateLineNumberListener {
  private final FileAnnotation myAnnotation;
  private int myLineNumber = -1;

  public GithubOpenCommitInBrowserFromAnnotateAction(FileAnnotation annotation) {
    myAnnotation = annotation;
  }

  @Nullable
  @Override
  protected CommitData getData(AnActionEvent e) {
    if (myLineNumber < 0) return null;

    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || virtualFile == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return null;

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile);
    if (repository == null || !GithubUtil.isRepositoryOnGitHub(repository)) return null;

    VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(myLineNumber);
    return new CommitData(project, repository, revisionNumber != null ? revisionNumber.asString() : null);
  }

  @Override
  public void consume(Integer integer) {
    myLineNumber = integer;
  }
}
