/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Checks if there are unresolved conflicts selected to commit.
 *
 * @author Kirill Likhodedov
 */
public class UnresolvedMergeCheckFactory extends CheckinHandlerFactory {
  @NotNull
  @Override
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new CheckinHandler() {
      @Override
      public ReturnResult beforeCheckin() {
        if (containsUnresolvedConflicts(panel)) {
          int answer = Messages.showYesNoDialog(panel.getComponent(), "Are you sure you want to commit changes with unresolved conflicts?",
                                                "Unresolved Conflicts", Messages.getWarningIcon());
          return answer == Messages.YES ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }
    };
  }

  private static boolean containsUnresolvedConflicts(@NotNull CheckinProjectPanel panel) {
    for (Change change : panel.getSelectedChanges()) {
      FileStatus status = change.getFileStatus();
      if (status.equals(FileStatus.MERGE) || status.equals(FileStatus.MERGED_WITH_BOTH_CONFLICTS) ||
          status.equals(FileStatus.MERGED_WITH_CONFLICTS) || status.equals(FileStatus.MERGED_WITH_PROPERTY_CONFLICTS)) {
        return true;
      }
    }
    return false;
  }
}
