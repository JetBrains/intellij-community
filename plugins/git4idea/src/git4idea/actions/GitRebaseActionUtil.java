/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.tryCast;

public class GitRebaseActionUtil {

  @NotNull
  public static AnAction abortRebaseForRepoAction(@NotNull GitRepository repository) {
    return createRepositoryRebaseAction("Git.Rebase.Abort", repository);
  }

  @NotNull
  public static AnAction continueRebaseForRepoAction(@NotNull GitRepository repository) {
    return createRepositoryRebaseAction("Git.Rebase.Continue", repository);
  }

  @NotNull
  public static AnAction skipCommitForRepoAction(@NotNull GitRepository repository) {
    return createRepositoryRebaseAction("Git.Rebase.Skip", repository);
  }

  @NotNull
  private static AnAction createRepositoryRebaseAction(@NotNull String rebaseActionId, @NotNull GitRepository repository) {
    GitAbstractRebaseAction rebaseAction =
      assertNotNull(tryCast(ActionManager.getInstance().getAction(rebaseActionId), GitAbstractRebaseAction.class));
    DumbAwareAction repositoryAction = new DumbAwareAction() {

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(repository.isRebaseInProgress());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        rebaseAction.performForRepoUnderProgress(repository);
      }
    };
    repositoryAction.getTemplatePresentation().copyFrom(rebaseAction.getTemplatePresentation());
    return repositoryAction;
  }
}
