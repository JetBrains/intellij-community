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
package com.intellij.vcs.log.impl;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer.getRequestTitle;
import static com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer.getRevisionTitle;

public class MergedChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @NotNull
  @Override
  public ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @NotNull Change change) {
    return change instanceof MergedChange && ((MergedChange)change).getSourceChanges().size() == 2;
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                             @NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws ProcessCanceledException, DiffRequestProducerException {
    return new MyProducer(presentable.getProject(), (MergedChange)presentable.getChange()).process(context, indicator);
  }

  @NotNull
  private static SimpleDiffRequest createRequest(@Nullable Project project,
                                                 @NotNull Change leftChange,
                                                 @NotNull Change rightChange,
                                                 @NotNull UserDataHolder context,
                                                 @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    String requestTitle = getRequestTitle(leftChange);

    ContentRevision leftRevision = leftChange.getBeforeRevision();
    ContentRevision centerRevision = leftChange.getAfterRevision();
    ContentRevision rightRevision = rightChange.getBeforeRevision();

    if (leftRevision == null) {
      return createTwoSideRequest(project, centerRevision, rightRevision, requestTitle,
                                  ChangeDiffRequestProducer.MERGED_VERSION, ChangeDiffRequestProducer.SERVER_VERSION,
                                  context, indicator);
    }
    else if (rightRevision == null) {
      return createTwoSideRequest(project, leftRevision, centerRevision, requestTitle,
                                  ChangeDiffRequestProducer.YOUR_VERSION, ChangeDiffRequestProducer.MERGED_VERSION,
                                  context, indicator);
    }
    else if (centerRevision == null) {
      return createTwoSideRequest(project, leftRevision, rightRevision, requestTitle,
                                  ChangeDiffRequestProducer.YOUR_VERSION, ChangeDiffRequestProducer.SERVER_VERSION,
                                  context, indicator);
    }
    return new SimpleDiffRequest(requestTitle,
                                 ChangeDiffRequestProducer.createContent(project, leftRevision, context, indicator),
                                 ChangeDiffRequestProducer.createContent(project, centerRevision, context, indicator),
                                 ChangeDiffRequestProducer.createContent(project, rightRevision, context, indicator),
                                 getRevisionTitle(leftRevision, ChangeDiffRequestProducer.YOUR_VERSION),
                                 getRevisionTitle(centerRevision, ChangeDiffRequestProducer.MERGED_VERSION),
                                 getRevisionTitle(rightRevision, ChangeDiffRequestProducer.SERVER_VERSION));
  }

  @NotNull
  private static SimpleDiffRequest createTwoSideRequest(@Nullable Project project,
                                                        @Nullable ContentRevision leftRevision,
                                                        @Nullable ContentRevision rightRevision,
                                                        @NotNull String requestTitle,
                                                        @NotNull String leftTitle,
                                                        @NotNull String rightTitle,
                                                        @NotNull UserDataHolder context,
                                                        @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    return new SimpleDiffRequest(requestTitle,
                                 ChangeDiffRequestProducer.createContent(project, leftRevision, context, indicator),
                                 ChangeDiffRequestProducer.createContent(project, rightRevision, context, indicator),
                                 getRevisionTitle(leftRevision, leftTitle),
                                 getRevisionTitle(rightRevision, rightTitle));
  }

  public static class MyProducer implements ChangeDiffRequestChain.Producer {
    @Nullable private final Project myProject;
    @NotNull private final MergedChange myMergedChange;

    public MyProducer(@Nullable Project project, @NotNull MergedChange mergedChange) {
      myProject = project;
      assert mergedChange.getSourceChanges().size() == 2;
      myMergedChange = mergedChange;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      List<Change> sourceChanges = myMergedChange.getSourceChanges();
      SimpleDiffRequest request = createRequest(myProject, sourceChanges.get(0), sourceChanges.get(1), context, indicator);
      request.putUserData(DiffUserDataKeys.THREESIDE_DIFF_WITH_RESULT, true);
      return request;
    }

    @NotNull
    @Override
    public String getName() {
      return getRequestTitle(myMergedChange);
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return ChangesUtil.getFilePath(myMergedChange);
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myMergedChange.getFileStatus();
    }
  }
}