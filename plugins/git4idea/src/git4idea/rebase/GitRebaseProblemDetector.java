/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.commands.GitLineHandlerListener;

/**
 * <p>
 * Detector of problems during rebase operations, that may need to perform additional steps to handle.
 * Such problems are: merge conflicts during applying commit;
 * "no changes" error which happens when the change is empty (for instance, a change was introduced in a commit, but then
 * discarded while merging. In that case 'git rebase --skip' is used.
 * </p>
 * <p>
 * To use the detector add it as a {@link GitLineHandlerListener} to {@link git4idea.commands.GitLineHandler}
 * </p>
 */
public class GitRebaseProblemDetector implements GitLineHandlerListener {
  private final static String[] REBASE_CONFLICT_INDICATORS = {
    "Merge conflict in",
    "after resolving the conflicts, mark the corrected paths",
    "After resolving the conflicts, mark them with",
    "Resolve all conflicts manually, mark them as resolved with",
    "You must edit all merge conflicts",
    "Failed to merge in the changes",
    "could not apply"};
  // since git 2.26 rebase backend changed to merge, git doesn't print this error
  private static final String REBASE_NO_CHANGE_INDICATOR = "No changes - did you forget to use 'git add'?";
  private static final String[] DIRTY_TREE_INDICATORS = {
    "you have unstaged changes",
    "your index contains uncommitted changes"
  };
  private static final String STOPPED_FOR_EDITING = "You can amend the commit now";

  private volatile boolean myMergeConflict;
  private volatile boolean myNoChangeError;
  private volatile boolean myDirtyTree;
  private volatile boolean myStoppedForEditing;

  public boolean isNoChangeError() {
    return myNoChangeError;
  }

  public boolean isMergeConflict() {
    return myMergeConflict;
  }

  public boolean isDirtyTree() {
    return myDirtyTree;
  }

  public boolean hasStoppedForEditing() {
    return myStoppedForEditing;
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
    for (String conflictIndicator : REBASE_CONFLICT_INDICATORS) {
      if (StringUtil.containsIgnoreCase(line, conflictIndicator)) {
        myMergeConflict = true;
        return;
      }
    }

    if (StringUtil.containsIgnoreCase(line, REBASE_NO_CHANGE_INDICATOR)) {
      myNoChangeError = true;
      return;
    }

    for (String indicator : DIRTY_TREE_INDICATORS) {
      if (StringUtil.containsIgnoreCase(line, indicator)) {
        myDirtyTree = true;
        return;
      }
    }

    if (StringUtil.containsIgnoreCase(line, STOPPED_FOR_EDITING)) {
      myStoppedForEditing = true;
    }
  }
}
