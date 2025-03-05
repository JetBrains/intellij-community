// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class CheckoutProviderEx implements CheckoutProvider {
  /**
   * @return a short unique identifier such as git, hg, svn, and so on
   */
  public abstract @NotNull String getVcsId();

  /**
   * Overloads CheckoutProvider#doCheckout(Project, Listener) to provide predefined repository URL
   * @deprecated should not be used outside VcsCloneComponentStub
   * Migrate to {@link com.intellij.util.ui.cloneDialog.VcsCloneDialog} or {@link com.intellij.openapi.vcs.ui.VcsCloneComponent}
   */
  @Deprecated(forRemoval = true)
  public abstract void doCheckout(final @NotNull Project project, @Nullable Listener listener, @Nullable String predefinedRepositoryUrl);
}
