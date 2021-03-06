// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitDefaultMergeDialogCustomizer;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.status.GitStagingAreaHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public final class GitConflictsToolWindowManager implements Disposable {
  @NotNull private final Project myProject;

  private final AtomicBoolean myRefreshScheduled = new AtomicBoolean();
  @Nullable private Content myContent;

  public GitConflictsToolWindowManager(@NotNull Project project) {
    myProject = project;
  }

  private void scheduleUpdate() {
    if (myRefreshScheduled.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> updateToolWindow(), c -> Disposer.isDisposed(this));
    }
  }

  private void updateToolWindow() {
    myRefreshScheduled.set(false);
    boolean hasConflicts = ContainerUtil.exists(GitRepositoryManager.getInstance(myProject).getRepositories(),
                                                repo -> !repo.getStagingAreaHolder().getAllConflicts().isEmpty());
    if (hasConflicts && myContent == null) {
      GitDefaultMergeDialogCustomizer mergeDialogCustomizer = new GitDefaultMergeDialogCustomizer(myProject);
      GitConflictsView panel = new GitConflictsView(myProject, mergeDialogCustomizer);
      myContent = ContentFactory.SERVICE.getInstance().createContent(panel.getComponent(), GitBundle.message("tab.title.conflicts"), false);
      myContent.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                            ChangesViewContentManager.TabOrderWeight.REPOSITORY.getWeight() + 1);
      myContent.setCloseable(false);
      myContent.setPreferredFocusedComponent(() -> panel.getPreferredFocusableComponent());
      Disposer.register(myContent, panel);
      ChangesViewContentManager.getInstance(myProject).addContent(myContent);
    }
    if (!hasConflicts && myContent != null) {
      ChangesViewContentManager.getInstance(myProject).removeContent(myContent);
      myContent = null;
    }
  }

  @Override
  public void dispose() {
    if (myContent != null) {
      ChangesViewContentManager.getInstance(myProject).removeContent(myContent);
      myContent = null;
    }
  }

  public static class MyStagingAreaListener implements GitStagingAreaHolder.StagingAreaListener {
    @Override
    public void stagingAreaChanged(@NotNull GitRepository repository) {
      if (!Registry.is("git.merge.conflicts.toolwindow")) return;

      Project project = repository.getProject();
      GitConflictsToolWindowManager service = project.getService(GitConflictsToolWindowManager.class);
      service.scheduleUpdate();
    }
  }

  public static class MyRepositoryListener implements VcsRepositoryMappingListener {
    private final Project myProject;

    public MyRepositoryListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void mappingChanged() {
      if (!Registry.is("git.merge.conflicts.toolwindow")) return;

      GitConflictsToolWindowManager service = myProject.getService(GitConflictsToolWindowManager.class);
      service.scheduleUpdate();
    }
  }
}
