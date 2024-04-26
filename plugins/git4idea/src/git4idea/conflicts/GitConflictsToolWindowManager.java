// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.conflicts;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.ContainerUtil;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitDefaultMergeDialogCustomizer;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.status.GitStagingAreaHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class GitConflictsToolWindowManager {
  public static final @NotNull @NonNls String CONFLICTS = "Git Conflicts";

  static final class ContentPredicate implements Predicate<Project> {
    @Override
    public boolean test(Project project) {
      if (!Registry.is("git.merge.conflicts.toolwindow")) return false;

      return ContainerUtil.exists(GitRepositoryManager.getInstance(project).getRepositories(),
                                  repo -> repo.getStagingAreaHolder().hasConflicts());
    }
  }

  static class ContentPreloader implements ChangesViewContentProvider.Preloader {
    @Override
    public void preloadTabContent(@NotNull Content content) {
      content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                          ChangesViewContentManager.TabOrderWeight.REPOSITORY.getWeight() + 1);
    }
  }

  static class ContentProvider implements ChangesViewContentProvider {
    private final Project myProject;

    ContentProvider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void initTabContent(@NotNull Content content) {
      GitDefaultMergeDialogCustomizer mergeDialogCustomizer = new GitDefaultMergeDialogCustomizer(myProject);
      GitConflictsView panel = new GitConflictsView(myProject, mergeDialogCustomizer);
      content.setComponent(panel.getComponent());
      content.setPreferredFocusedComponent(() -> panel.getPreferredFocusableComponent());
      content.setDisposer(panel);
    }
  }

  public static class DisplayNameSupplier implements Supplier<String> {
    @Override
    public String get() {
      return GitBundle.message("tab.title.conflicts");
    }
  }


  public static class MyStagingAreaListener implements GitStagingAreaHolder.StagingAreaListener {
    @Override
    public void stagingAreaChanged(@NotNull GitRepository repository) {
      if (!Registry.is("git.merge.conflicts.toolwindow")) return;

      Project project = repository.getProject();
      ApplicationManager.getApplication().invokeLater(() -> {
        project.getMessageBus().syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged();
      }, ModalityState.nonModal(), project.getDisposed());
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

      ApplicationManager.getApplication().invokeLater(() -> {
        myProject.getMessageBus().syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged();
      }, ModalityState.nonModal());
    }
  }
}
