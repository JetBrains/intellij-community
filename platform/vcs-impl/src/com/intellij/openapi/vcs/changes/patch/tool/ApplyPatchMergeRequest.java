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
  @Nullable private final Project myProject;

  @NotNull private final DocumentContent myResultContent;
  @NotNull private final AppliedTextPatch myAppliedPatch;

  @NotNull private final CharSequence myOriginalContent;
  @NotNull private final @NonNls String myLocalContent;

  @Nullable private final @NlsContexts.DialogTitle String myWindowTitle;
  @NotNull private final @NlsContexts.Label String myLocalTitle;
  @NotNull private final @NlsContexts.Label String myResultTitle;
  @NotNull private final @NlsContexts.Label String myPatchTitle;

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

  @Nullable
  public Project getProject() {
    return myProject;
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
