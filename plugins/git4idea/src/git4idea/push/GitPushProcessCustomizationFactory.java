// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Implement to customize some parts of the {@link GitPushOperation git push process}.
 * <br/><br/>
 * Note that only one customization can be installed: if several ones are found among installed plugins, none is being executed.
 */
public interface GitPushProcessCustomizationFactory {

  ExtensionPointName<GitPushProcessCustomizationFactory> GIT_PUSH_CUSTOMIZATION_FACTORY_EP =
    ExtensionPointName.create("com.intellij.vcs.git.pushCustomizationFactory");

  /**
   * Creates a push customization instance for a push session, if such customization is allowed in the given conditions.
   */
  @Nullable
  GitPushProcessCustomization createCustomization(@NotNull Project project,
                                                  @NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                                                  boolean forcePush);

  interface GitPushProcessCustomization {

    /**
     * Will be executed after one push iteration,
     * i.e. after all `git push` commands are called against all selected repositories,
     * but before the rebase/merge is proposed or a notification is shown.
     *
     * @param results Results of `git push` commands.
     * @return Adjusted results of the push process - these will be processed by the push process instead of the original ones.
     */
    @NotNull
    Map<GitRepository, GitPushRepoResult> executeAfterPushIteration(@NotNull Map<GitRepository, GitPushRepoResult> results);

    /**
     * Overrides the call to `git push`.
     */
    @NotNull
    GitCommandResult runPushCommand(@NotNull GitRepository repository,
                                    @NotNull PushSpec<GitPushSource, GitPushTarget> pushSpec,
                                    @NotNull GitPushParams pushParams,
                                    @NotNull GitLineHandlerListener progressListener);

    /**
     * Will be executed after the whole push procedure is finished, successfully or not.
     */
    void executeAfterPush(@NotNull Map<GitRepository, GitPushRepoResult> results);
  }
}
