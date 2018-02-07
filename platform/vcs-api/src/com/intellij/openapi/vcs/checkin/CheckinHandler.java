/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.LocalCommitExecutor;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A callback which can be used to extend the user interface of the Checkin Project/Checkin File
 * dialogs and to perform actions before commit, on successful commit and on failed commit.
 *
 * @author lesya
 * @since 5.1
 * @see BaseCheckinHandlerFactory#createHandler(com.intellij.openapi.vcs.CheckinProjectPanel, CommitContext)
 * @see CodeAnalysisBeforeCheckinHandler
 */
public abstract class CheckinHandler {
  /**
   * you can return this handler if your handler shouldn't be created (for instance, your VCS is not active)
   */
  public static final CheckinHandler DUMMY = new CheckinHandler() {
  };

  public enum ReturnResult {
    COMMIT, CANCEL, CLOSE_WINDOW
  }

  /**
   * Returns the panel which is inserted in the "Before Check In" group box of the Checkin Project
   * or Checkin File dialogs.
   *
   * @return the panel instance, or null if the handler does not provide any options to show in the
   * "Before Check In" group.
   */
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    return null;
  }

  /**
   * Returns the panel which is inserted in the "After Check In" group box of the Checkin Project
   * or Checkin File dialogs.
   *
   * @return the panel instance, or null if the handler does not provide any options to show in the
   * "After Check In" group.
   * @param parentDisposable
   */
  @Nullable
  public RefreshableOnComponent getAfterCheckinConfigurationPanel(final Disposable parentDisposable) {
    return null;
  }

  /**
   * Performs the before check-in processing when a custom commit executor is used. The method can use the
   * {@link com.intellij.openapi.vcs.CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(com.intellij.openapi.vcs.CheckinProjectPanel, CommitContext)} to
   * get information about the files to be checked in.
   *
   * @param executor the commit executor, or null if the standard commit operation is executed.
   * @param additionalDataConsumer
   * @return the code indicating whether the check-in operation should be performed or aborted.
   */
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    return beforeCheckin();
  }

  /**
   * Performs the before check-in processing. The method can use the
   * {@link com.intellij.openapi.vcs.CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(com.intellij.openapi.vcs.CheckinProjectPanel, CommitContext)} to
   * get information about the files to be checked in.
   *
   * @return the code indicating whether the check-in operation should be performed or aborted.
   */
  public ReturnResult beforeCheckin() {
    return ReturnResult.COMMIT;
  }

  /**
   * Performs the processing on successful check-in. The method can use the
   * {@link com.intellij.openapi.vcs.CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(com.intellij.openapi.vcs.CheckinProjectPanel, CommitContext)} to
   * get information about the checked in files.
   */
  public void checkinSuccessful() {

  }

  /**
   * Performs the processing on failed check-in. The method can use the
   * {@link com.intellij.openapi.vcs.CheckinProjectPanel} instance passed to
   * {@link BaseCheckinHandlerFactory#createHandler(com.intellij.openapi.vcs.CheckinProjectPanel, CommitContext)} to
   * get information about the checked in files.
   *
   * @param exception the list of VCS exceptions identifying the problems that occurred during the
   * commit operation.
   */
  public void checkinFailed(List<VcsException> exception) {

  }

  /**
   * Called to notify handler that user has included/excluded some changes to/from commit
   */
  public void includedChangesChanged() {
  }

  /**
   * allows to skip before checkin steps when is not applicable. E.g. there should be no check for todos before shelf/create patch 
   * @param executor current operation (null for commit)
   * @return true if handler should be skipped
   */
  public boolean acceptExecutor(CommitExecutor executor) {
    return !(executor instanceof LocalCommitExecutor);
  }
}
