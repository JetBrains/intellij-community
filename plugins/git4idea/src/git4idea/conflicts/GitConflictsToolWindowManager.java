// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitConflictsToolWindowManager implements ProjectComponent {
  public static final String TAB_NAME = "Conflicts";

  @NotNull private final Project myProject;
  @NotNull private final ChangesViewContentI myContentManager;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  private final MergingUpdateQueue myQueue;

  @Nullable private Content myContent;

  public GitConflictsToolWindowManager(@NotNull Project project,
                                       @NotNull ChangesViewContentI contentManager,
                                       @NotNull ChangeListManager changeListManager,
                                       @NotNull GitRepositoryManager repositoryManager) {
    myProject = project;
    myContentManager = contentManager;
    myRepositoryManager = repositoryManager;

    myQueue = new MergingUpdateQueue("GitConflictsToolWindowManager", 300, true, null);

    changeListManager.addChangeListListener(new ChangeListListener() {
      @Override
      public void changeListUpdateDone() {
        myQueue.queue(Update.create("update", () -> updateToolWindow()));
      }
    });
  }

  private void updateToolWindow() {
    if (!Registry.is("git.merge.conflicts.toolwindow")) return;

    boolean hasConflicts = ContainerUtil.exists(myRepositoryManager.getRepositories(),
                                                repo -> !repo.getConflictsHolder().getConflicts().isEmpty());
    if (hasConflicts && myContent == null) {
      GitConflictsView panel = new GitConflictsView(myProject);
      myContent = ContentFactory.SERVICE.getInstance().createContent(panel.getComponent(), TAB_NAME, false);
      myContent.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                            ChangesViewContentManager.TabOrderWeight.REPOSITORY.getWeight() + 1);
      myContent.setCloseable(false);
      myContent.setPreferredFocusedComponent(() -> panel.getPreferredFocusableComponent());
      Disposer.register(myContent, panel);
      myContentManager.addContent(myContent);
    }
    if (!hasConflicts && myContent != null) {
      myContentManager.removeContent(myContent);
      myContent = null;
    }
  }
}
