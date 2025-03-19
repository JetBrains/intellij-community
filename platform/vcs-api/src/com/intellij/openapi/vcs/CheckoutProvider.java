// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.VcsCloneComponent;
import com.intellij.openapi.vcs.ui.VcsCloneComponentStub;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Comparator;

/**
 * Implement this interface and register it as extension to checkoutProvider extension point in order to provide checkout
 */
public interface CheckoutProvider {
  @NonNls ExtensionPointName<CheckoutProvider> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.checkoutProvider");

  /**
   * @param project current project or default project if no project is open.
   * @deprecated should not be used outside VcsCloneComponentStub
   * Migrate to {@link com.intellij.util.ui.cloneDialog.VcsCloneDialog} or {@link VcsCloneComponent}
   */
  @Deprecated(forRemoval = true)
  void doCheckout(final @NotNull Project project, @Nullable Listener listener);

  @Nls @NotNull String getVcsName();

  interface Listener {
    @RequiresBackgroundThread
    void directoryCheckedOut(File directory, VcsKey vcs);

    @RequiresBackgroundThread
    void checkoutCompleted();
  }

  class CheckoutProviderComparator implements Comparator<CheckoutProvider> {
    @Override
    public int compare(final @NotNull CheckoutProvider o1, final @NotNull CheckoutProvider o2) {
      return UIUtil.removeMnemonic(o1.getVcsName()).compareTo(UIUtil.removeMnemonic(o2.getVcsName()));
    }
  }

  default @NotNull VcsCloneComponent buildVcsCloneComponent(@NotNull Project project, @NotNull ModalityState modalityState, @NotNull VcsCloneDialogComponentStateListener dialogStateListener) {
    return new VcsCloneComponentStub(project, this, VcsBundle.message("clone.dialog.clone.button"));
  }
}
