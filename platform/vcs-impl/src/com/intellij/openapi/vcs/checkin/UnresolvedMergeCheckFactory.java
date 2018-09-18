// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks if there are unresolved conflicts selected to commit.
 *
 * @author Kirill Likhodedov
 */
public class UnresolvedMergeCheckFactory extends CheckinHandlerFactory {
  @NotNull
  @Override
  public CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new CheckinHandler() {
      @Override
      public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        for (UnresolvedMergeCheckProvider extension : UnresolvedMergeCheckProvider.EP_NAME.getExtensions()) {
          ReturnResult result = extension.checkUnresolvedConflicts(panel, commitContext, executor);
          if (result != null) return result;
        }
        return performDefaultCheck(panel);
      }
    };
  }

  @NotNull
  private static ReturnResult performDefaultCheck(@NotNull CheckinProjectPanel panel) {
    if (containsUnresolvedConflicts(panel)) {
      int answer = Messages.showYesNoDialog(panel.getComponent(), "Are you sure you want to commit changes with unresolved conflicts?",
                                            "Unresolved Conflicts", Messages.getWarningIcon());
      if (answer != Messages.YES) return ReturnResult.CANCEL;
    }
    return ReturnResult.COMMIT;
  }

  private static boolean containsUnresolvedConflicts(@NotNull CheckinProjectPanel panel) {
    for (Change change : panel.getSelectedChanges()) {
      FileStatus status = change.getFileStatus();
      if (status.equals(FileStatus.MERGE) ||
          status.equals(FileStatus.MERGED_WITH_BOTH_CONFLICTS) ||
          status.equals(FileStatus.MERGED_WITH_CONFLICTS) ||
          status.equals(FileStatus.MERGED_WITH_PROPERTY_CONFLICTS)) {
        return true;
      }
    }
    return false;
  }
}
