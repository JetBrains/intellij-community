/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NonNls;

public interface VcsActions {

  @NonNls String ACTION_COPY_REVISION_NUMBER = "Vcs.CopyRevisionNumberAction";
  @NonNls String VCS_OPERATIONS_POPUP = "Vcs.Operations.Popup";
  @NonNls String DIFF_BEFORE_WITH_LOCAL = "Vcs.ShowDiffWithLocal.Before";
  @NonNls String DIFF_AFTER_WITH_LOCAL = "Vcs.ShowDiffWithLocal";

  @NonNls String PRIMARY_COMMIT_EXECUTORS_GROUP = "Vcs.Commit.PrimaryCommitActions";
  @NonNls String COMMIT_EXECUTORS_GROUP = "Vcs.CommitExecutor.Actions";
}
