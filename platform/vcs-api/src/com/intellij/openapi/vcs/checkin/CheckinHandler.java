// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.LocalCommitExecutor;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * A callback which can be used to extend the user interface of the Checkin Project/Checkin File
 * dialogs and to perform actions before commit, on successful commit and on failed commit.
 * <p>
 * Handlers may also implement {@link CommitCheck} interface, that supersedes {@link #beforeCheckin} method.
 *
 * @see BaseCheckinHandlerFactory#createHandler(CheckinProjectPanel, CommitContext)
 * @see CodeAnalysisBeforeCheckinHandler
 * @see CheckinModificationHandler
 */
public abstract class CheckinHandler {
  /**
   * Return this handler if your handler shouldn't be created (for instance, your VCS is not active).
   */
  public static final CheckinHandler DUMMY = new CheckinHandler() {
  };

  public enum ReturnResult {
    COMMIT, CANCEL, CLOSE_WINDOW
  }

  /**
   * Returns the panel which is inserted in the "Before Commit" group of the commit options panel.
   *
   * @see com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
   */
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return null;
  }

  /**
   * Returns the panel which is inserted in the "Settings | Version Control | Commit" configurable panel.
   *
   * @see com.intellij.openapi.options.UiDslUnnamedConfigurable
   */
  @Nullable
  public UnnamedConfigurable getBeforeCheckinSettings() {
    return tryCast(getBeforeCheckinConfigurationPanel(), UnnamedConfigurable.class);
  }

  /**
   * Returns the panel which is inserted in the "After Commit" group of the commit options panel.
   */
  @Nullable
  public RefreshableOnComponent getAfterCheckinConfigurationPanel(final Disposable parentDisposable) {
    return null;
  }

  /**
   * Performs the before check-in processing when a custom commit executor is used. The method can use the
   * {@link CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(CheckinProjectPanel, CommitContext)} to
   * get information about the files to be checked in.
   * <p>
   * This method will not be called if {@link CommitCheck} interface is implemented.
   *
   * @param executor the commit executor, or {@code null} if the standard commit operation is executed.
   * @return the code indicating whether the check-in operation should be performed or aborted.
   */
  @Nullable
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    return beforeCheckin();
  }

  /**
   * Performs the before check-in processing. The method can use the
   * {@link CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(CheckinProjectPanel, CommitContext)} to
   * get information about the files to be checked in.
   *
   * @return the code indicating whether the check-in operation should be performed or aborted.
   */
  public ReturnResult beforeCheckin() {
    return ReturnResult.COMMIT;
  }

  /**
   * Performs the processing on successful check-in. The method can use the
   * {@link CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(CheckinProjectPanel, CommitContext)} to
   * get information about the checked in files.
   */
  public void checkinSuccessful() {

  }

  /**
   * Performs the processing on failed check-in. The method can use the
   * {@link CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(CheckinProjectPanel, CommitContext)} to
   * get information about the checked in files.
   *
   * @param exception the list of VCS exceptions identifying the problems that occurred during the
   *                  commit operation.
   */
  public void checkinFailed(List<VcsException> exception) {

  }

  /**
   * Called to notify handler that user has included/excluded some changes to/from commit.
   */
  public void includedChangesChanged() {
  }

  /**
   * Allows to skip {@link #beforeCheckin()} steps when not applicable.
   * E.g., there should be no check for todos before shelf/create patch.
   *
   * @param executor current operation ({@code null} for VCS commit)
   * @return {@code true} if handler should be skipped
   */
  public boolean acceptExecutor(CommitExecutor executor) {
    return !(executor instanceof LocalCommitExecutor);
  }
}
