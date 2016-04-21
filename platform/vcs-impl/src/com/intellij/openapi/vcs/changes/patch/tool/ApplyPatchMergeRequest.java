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

import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyPatchMergeRequest extends MergeRequest {
  @Nullable private final Project myProject;

  @NotNull private final Document myDocument;
  @NotNull private final AppliedTextPatch myAppliedPatch;

  @NotNull private final CharSequence myOriginalContent;
  @NotNull private final String myLocalContent;

  @NotNull private final String myWindowTitle;
  @NotNull private final String myLocalTitle;
  @NotNull private final String myResultTitle;
  @NotNull private final String myPatchTitle;

  @Nullable private final Consumer<MergeResult> myCallback;

  public ApplyPatchMergeRequest(@Nullable Project project,
                                @NotNull Document document,
                                @NotNull AppliedTextPatch appliedPatch,
                                @NotNull String localContent,
                                @NotNull String windowTitle,
                                @NotNull String localTitle,
                                @NotNull String resultTitle,
                                @NotNull String patchTitle,
                                @Nullable Consumer<MergeResult> callback) {
    myProject = project;
    myDocument = document;
    myAppliedPatch = appliedPatch;

    myOriginalContent = ApplicationManager.getApplication().runReadAction(new Computable<CharSequence>() {
      @Override
      public CharSequence compute() {
        return myDocument.getImmutableCharSequence();
      }
    });
    myLocalContent = localContent;

    myWindowTitle = windowTitle;
    myLocalTitle = localTitle;
    myResultTitle = resultTitle;
    myPatchTitle = patchTitle;

    myCallback = callback;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public String getLocalContent() {
    return myLocalContent;
  }

  @NotNull
  public AppliedTextPatch getPatch() {
    return myAppliedPatch;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myWindowTitle;
  }

  @NotNull
  public String getLocalTitle() {
    return myLocalTitle;
  }

  @NotNull
  public String getResultTitle() {
    return myResultTitle;
  }

  @NotNull
  public String getPatchTitle() {
    return myPatchTitle;
  }

  @Override
  public void applyResult(@NotNull MergeResult result) {
    final CharSequence applyContent;
    switch (result) {
      case CANCEL:
        applyContent = myOriginalContent;
        break;
      case LEFT:
        applyContent = myLocalContent;
        break;
      case RIGHT:
        applyContent = PatchChangeBuilder.getPatchedContent(myAppliedPatch, myLocalContent);
        break;
      case RESOLVED:
        applyContent = null;
        break;
      default:
        throw new IllegalArgumentException(result.name());
    }

    if (applyContent != null) {
      new WriteCommandAction.Simple(myProject) {
        @Override
        protected void run() throws Throwable {
          myDocument.setText(applyContent);
        }
      }.execute();
    }

    if (myCallback != null) myCallback.consume(result);
  }
}
