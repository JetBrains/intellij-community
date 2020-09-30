/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyPatchDiffRequest extends DiffRequest implements ApplyPatchRequest {
  @NotNull private final DocumentContent myResultContent;
  @NotNull private final AppliedTextPatch myAppliedPatch;

  @NotNull private final @NonNls String myLocalContent;

  @Nullable private final @NlsContexts.DialogTitle String myWindowTitle;
  @NotNull private final @NlsContexts.Label String myLocalTitle;
  @NotNull private final @NlsContexts.Label String myResultTitle;
  @NotNull private final @NlsContexts.Label String myPatchTitle;

  public ApplyPatchDiffRequest(@NotNull DocumentContent resultContent,
                               @NotNull AppliedTextPatch appliedPatch,
                               @NotNull @NonNls String localContent,
                               @Nullable @NlsContexts.DialogTitle String windowTitle,
                               @NotNull @NlsContexts.Label String localTitle,
                               @NotNull @NlsContexts.Label String resultTitle,
                               @NotNull @NlsContexts.Label String patchTitle) {
    myResultContent = resultContent;
    myAppliedPatch = appliedPatch;
    myLocalContent = localContent;
    myWindowTitle = windowTitle;
    myLocalTitle = localTitle;
    myResultTitle = resultTitle;
    myPatchTitle = patchTitle;
  }

  @Override
  @NotNull
  public DocumentContent getResultContent() {
    return myResultContent;
  }

  @Override
  @NotNull
  public String getLocalContent() {
    return myLocalContent;
  }

  @Override
  @NotNull
  public AppliedTextPatch getPatch() {
    return myAppliedPatch;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myWindowTitle;
  }

  @Override
  @NotNull
  public String getLocalTitle() {
    return myLocalTitle;
  }

  @Override
  @NotNull
  public String getResultTitle() {
    return myResultTitle;
  }

  @Override
  @NotNull
  public String getPatchTitle() {
    return myPatchTitle;
  }
}
