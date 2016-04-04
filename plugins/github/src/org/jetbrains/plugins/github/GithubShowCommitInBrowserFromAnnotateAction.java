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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubUtil;

/**
 * @author Kirill Likhodedov
 */
public class GithubShowCommitInBrowserFromAnnotateAction extends GithubShowCommitInBrowserAction implements UpToDateLineNumberListener {

  private final FileAnnotation myAnnotation;
  private int myLineNumber = -1;

  public GithubShowCommitInBrowserFromAnnotateAction(FileAnnotation annotation) {
    super();
    myAnnotation = annotation;
  }

  @Override
  public void update(AnActionEvent e) {
    EventData eventData = calcData(e, myLineNumber);
    if (eventData == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setEnabled(myLineNumber >= 0 && myAnnotation.getLineRevisionNumber(myLineNumber) != null);
    e.getPresentation().setVisible(GithubUtil.isRepositoryOnGitHub(eventData.getRepository()));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EventData eventData = calcData(e, myLineNumber);
    if (eventData == null) {
      return;
    }

    final VcsRevisionNumber revisionNumber = myAnnotation.getLineRevisionNumber(myLineNumber);
    if (revisionNumber != null) {
      openInBrowser(eventData.getProject(), eventData.getRepository(), revisionNumber.asString());
    }
  }

  @Nullable
  private static EventData calcData(AnActionEvent e, int lineNumber) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || virtualFile == null) {
      return null;
    }
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return null;
    }

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile);
    if (repository == null) {
      return null;
    }

    return new EventData(project, repository);
  }

  @Override
  public void consume(Integer integer) {
    myLineNumber = integer;
  }

  private static class EventData {
    @NotNull private final Project myProject;
    @NotNull private final GitRepository myRepository;

    private EventData(@NotNull Project project, @NotNull GitRepository repository) {
      myProject = project;
      myRepository = repository;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public GitRepository getRepository() {
      return myRepository;
    }
  }
}
