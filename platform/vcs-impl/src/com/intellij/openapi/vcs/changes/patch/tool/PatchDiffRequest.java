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
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatchDiffRequest extends DiffRequest {
  @NotNull private final AppliedTextPatch myAppliedPatch;

  @Nullable private final @NlsContexts.DialogTitle String myWindowTitle;
  @Nullable private final @NlsContexts.Label String myPanelTitle;


  public PatchDiffRequest(@NotNull AppliedTextPatch appliedPatch) {
    this(appliedPatch, null, null);
  }

  public PatchDiffRequest(@NotNull AppliedTextPatch patch,
                          @Nullable @NlsContexts.DialogTitle String windowTitle,
                          @Nullable @NlsContexts.Label String panelTitle) {
    myAppliedPatch = patch;
    myWindowTitle = windowTitle;
    myPanelTitle = panelTitle;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myWindowTitle;
  }

  @NlsContexts.Label
  @Nullable
  public String getPanelTitle() {
    return myPanelTitle;
  }

  @NotNull
  public AppliedTextPatch getPatch() {
    return myAppliedPatch;
  }
}
