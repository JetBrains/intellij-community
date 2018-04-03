/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.impl.DiffToolSubstitutor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalChangeListDiffTool implements FrameDiffTool, DiffToolSubstitutor {
  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    LocalChangeListDiffRequest localRequest = (LocalChangeListDiffRequest)request;
    return new SimpleLocalChangeListDiffViewer(context, localRequest);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof LocalChangeListDiffRequest)) return false;
    LocalChangeListDiffRequest localRequest = (LocalChangeListDiffRequest)request;
    return localRequest.getLineStatusTracker() instanceof PartialLocalLineStatusTracker;
  }

  @NotNull
  @Override
  public String getName() {
    return SimpleDiffTool.INSTANCE.getName();
  }

  @Nullable
  @Override
  public DiffTool getReplacement(@NotNull DiffTool tool, @NotNull DiffContext context, @NotNull DiffRequest request) {
    if (tool != SimpleDiffTool.INSTANCE) return null;
    if (!canShow(context, request)) return null;
    return this;
  }
}
