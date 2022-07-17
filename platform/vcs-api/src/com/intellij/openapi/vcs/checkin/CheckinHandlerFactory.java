// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;

/**
 * Provides {@link CheckinHandler} to the commit flow.
 *
 * @see VcsCheckinHandlerFactory
 */
public abstract class CheckinHandlerFactory implements BaseCheckinHandlerFactory {
  public static final ExtensionPointName<CheckinHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.checkinHandlerFactory");

  /**
   * Creates {@link CheckinHandler}. Called for each commit operation.
   *
   * @param panel         contains common commit data (e.g. commit message, files to commit)
   * @param commitContext contains specific commit data (e.g. if "amend commit" should be performed)
   * @return handler instance or {@link CheckinHandler#DUMMY} if no handler is necessary
   */
  @Override
  @NotNull
  public abstract CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext);

  /**
   * Creates {@link BeforeCheckinDialogHandler}. Called for each commit operation. Only used for Commit Dialog.
   *
   * @param project project where commit is performed
   * @return handler instance or {@code null} if no handler is necessary
   */
  @Override
  public BeforeCheckinDialogHandler createSystemReadyHandler(@NotNull Project project) {
    return null;
  }
}
