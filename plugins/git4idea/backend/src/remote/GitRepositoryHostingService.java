// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote;

import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitRepositoryHostingService implements RepositoryHostingService {
  public static final ExtensionPointName<GitRepositoryHostingService> EP_NAME =
    ExtensionPointName.create("Git4Idea.gitRepositoryHostingService");

  /**
   * @see InteractiveGitHttpAuthDataProvider
   */
  @RequiresBackgroundThread
  public @Nullable InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(@NotNull Project project, @NotNull String url) {
    return null;
  }

  /**
   * @see InteractiveGitHttpAuthDataProvider
   */
  @RequiresBackgroundThread
  public @Nullable InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(@NotNull Project project, @NotNull String url, @NotNull String login) {
    return null;
  }
}
