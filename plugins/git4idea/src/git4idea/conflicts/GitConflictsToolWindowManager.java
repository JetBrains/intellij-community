// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import git4idea.repo.GitConflictsHolder;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitConflictsToolWindowManager {
  public static final String TAB_NAME = "Conflicts";

  @NotNull private final Project myProject;

  private final MergingUpdateQueue myQueue;

  @Nullable private Content myContent;

  public GitConflictsToolWindowManager(@NotNull Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("GitConflictsToolWindowManager", 300, true, null, myProject);
  }

  private void init() {
    myProject.getMessageBus().connect().subscribe(GitConflictsHolder.CONFLICTS_CHANGE, new GitConflictsHolder.ConflictsListener() {
      @Override
      public void conflictsChanged(@NotNull GitRepository repository) {
        myQueue.queue(Update.create("update", () -> updateToolWindow()));
      }
    });

    ApplicationManager.getApplication().invokeLater(() -> updateToolWindow());
  }

  private void updateToolWindow() {
    if (!Registry.is("git.merge.conflicts.toolwindow")) return;

    boolean hasConflicts = ContainerUtil.exists(GitRepositoryManager.getInstance(myProject).getRepositories(),
                                                repo -> !repo.getConflictsHolder().getConflicts().isEmpty());
    if (hasConflicts && myContent == null) {
      GitConflictsView panel = new GitConflictsView(myProject);
      myContent = ContentFactory.SERVICE.getInstance().createContent(panel.getComponent(), TAB_NAME, false);
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

  public static class Starter implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      new GitConflictsToolWindowManager(project).init();
    }
  }
}
