// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyPatchDiffRequest extends DiffRequest implements ApplyPatchRequest {
  private final @NotNull DocumentContent myResultContent;
  private final @NotNull AppliedTextPatch myAppliedPatch;

  private final @NotNull @NonNls String myLocalContent;

  private final @Nullable @NlsContexts.DialogTitle String myWindowTitle;
  private final @NotNull @NlsContexts.Label String myLocalTitle;
  private final @NotNull @NlsContexts.Label String myResultTitle;
  private final @NotNull @NlsContexts.Label String myPatchTitle;

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
  public @NotNull DocumentContent getResultContent() {
    return myResultContent;
  }

  @Override
  public @NotNull String getLocalContent() {
    return myLocalContent;
  }

  @Override
  public @NotNull AppliedTextPatch getPatch() {
    return myAppliedPatch;
  }

  @Override
  public @Nullable String getTitle() {
    return myWindowTitle;
  }

  @Override
  public @NotNull String getLocalTitle() {
    return myLocalTitle;
  }

  @Override
  public @NotNull String getResultTitle() {
    return myResultTitle;
  }

  @Override
  public @NotNull String getPatchTitle() {
    return myPatchTitle;
  }
}
