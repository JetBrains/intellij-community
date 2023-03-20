/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.HelpIdProvider;
import com.intellij.openapi.extensions.ProjectExtensionPointName;

/**
 * Marker class for executors that should be skipped by most {@link com.intellij.openapi.vcs.checkin.CheckinHandler} pre-commit checks.
 * See {@link com.intellij.openapi.vcs.checkin.CheckinHandler#acceptExecutor(CommitExecutor)}.
 */
public abstract class LocalCommitExecutor implements CommitExecutor, HelpIdProvider {
  /**
   * @deprecated Use {@link CommitExecutor#LOCAL_COMMIT_EXECUTOR} instead.
   */
  @Deprecated
  public static final ProjectExtensionPointName<CommitExecutor> LOCAL_COMMIT_EXECUTOR = CommitExecutor.LOCAL_COMMIT_EXECUTOR;
}
