// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyPatchMergeRequest extends MergeRequest implements ApplyPatchRequest {
  private final @Nullable Project myProject;

  private final @NotNull DocumentContent myResultContent;
  private final @NotNull AppliedTextPatch myAppliedPatch;

  private final @NotNull CharSequence myOriginalContent;
  private final @NotNull @NonNls String myLocalContent;

  private final @Nullable @NlsContexts.DialogTitle String myWindowTitle;
  private final @NotNull @NlsContexts.Label String myLocalTitle;
  private final @NotNull @NlsContexts.Label String myResultTitle;
  private final @NotNull @NlsContexts.Label String myPatchTitle;

  public ApplyPatchMergeRequest(@Nullable Project project,
                                @NotNull DocumentContent resultContent,
                                @NotNull AppliedTextPatch appliedPatch,
                                @NotNull @NonNls String localContent,
                                @Nullable @NlsContexts.DialogTitle String windowTitle,
                                @NotNull @NlsContexts.Label String localTitle,
                                @NotNull @NlsContexts.Label String resultTitle,
                                @NotNull @NlsContexts.Label String patchTitle) {
    myProject = project;
    myResultContent = resultContent;
    myAppliedPatch = appliedPatch;

    myOriginalContent = ReadAction.compute(() -> myResultContent.getDocument().getImmutableCharSequence());
    myLocalContent = localContent;

    myWindowTitle = windowTitle;
    myLocalTitle = localTitle;
    myResultTitle = resultTitle;
    myPatchTitle = patchTitle;
  }

  public @Nullable Project getProject() {
    return myProject;
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

  @Override
  public void applyResult(@NotNull MergeResult result) {
    final CharSequence applyContent = switch (result) {
      case CANCEL -> MergeUtil.shouldRestoreOriginalContentOnCancel(this) ? myOriginalContent : null;
      case LEFT -> myLocalContent;
      case RIGHT -> throw new UnsupportedOperationException();
      case RESOLVED -> null;
    };

    if (applyContent != null) {
      WriteCommandAction.writeCommandAction(myProject).run(() -> myResultContent.getDocument().setText(applyContent));
    }

    MergeCallback.getCallback(this).applyResult(result);
  }
}
