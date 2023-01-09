// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer.getRequestTitle;

public class MergedChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @Override
  public @NotNull ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @NotNull Change change) {
    return change instanceof MergedChange && ((MergedChange)change).getSourceChanges().size() == 2;
  }

  @Override
  public @NotNull DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                                      @NotNull UserDataHolder context,
                                      @NotNull ProgressIndicator indicator) throws ProcessCanceledException, DiffRequestProducerException {
    return new MyProducer(presentable.getProject(), (MergedChange)presentable.getChange()).process(context, indicator);
  }

  private static @NotNull SimpleDiffRequest createTwoSideRequest(@Nullable Project project,
                                                                 @Nullable ContentRevision leftRevision,
                                                                 @Nullable ContentRevision rightRevision,
                                                                 @NotNull @NlsContexts.DialogTitle String requestTitle,
                                                                 @NotNull @NlsContexts.Label String leftTitle,
                                                                 @NotNull @NlsContexts.Label String rightTitle,
                                                                 @NotNull UserDataHolder context,
                                                                 @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    return new SimpleDiffRequest(requestTitle,
                                 ChangeDiffRequestProducer.createContent(project, leftRevision, context, indicator),
                                 ChangeDiffRequestProducer.createContent(project, rightRevision, context, indicator),
                                 leftTitle, rightTitle);
  }

  private static @NotNull @Nls String getRevisionTitle(@NotNull Map<Key<?>, Object> context,
                                                       @NotNull Key<@Nls String> key,
                                                       @Nullable ContentRevision revision,
                                                       @NotNull @Nls String defaultTitle) {
    @Nls String titleFromContext = (String)context.get(key);
    if (titleFromContext != null) return titleFromContext;
    return ChangeDiffRequestProducer.getRevisionTitle(revision, defaultTitle);
  }

  public static final class MyProducer implements ChangeDiffRequestChain.Producer {
    private final @Nullable Project myProject;
    private final @NotNull MergedChange myMergedChange;
    private final @NotNull Map<Key<?>, Object> myContext;

    public MyProducer(@Nullable Project project,
                      @NotNull MergedChange mergedChange,
                      @NotNull Map<Key<?>, Object> context) {
      myProject = project;
      myContext = context;
      assert mergedChange.getSourceChanges().size() == 2;
      myMergedChange = mergedChange;
    }

    public MyProducer(@Nullable Project project,
                      @NotNull MergedChange mergedChange) {
      this(project, mergedChange, Collections.emptyMap());
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      List<Change> sourceChanges = myMergedChange.getSourceChanges();
      SimpleDiffRequest request = createRequest(myProject, sourceChanges.get(0), sourceChanges.get(1), context, indicator);
      request.putUserData(DiffUserDataKeys.THREESIDE_DIFF_COLORS_MODE, DiffUserDataKeys.ThreeSideDiffColors.MERGE_RESULT);
      return request;
    }

    private @NotNull SimpleDiffRequest createRequest(@Nullable Project project,
                                                     @NotNull Change leftChange,
                                                     @NotNull Change rightChange,
                                                     @NotNull UserDataHolder context,
                                                     @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException {
      String requestTitle = getRequestTitle(leftChange);

      ContentRevision leftRevision = leftChange.getBeforeRevision();
      ContentRevision centerRevision = leftChange.getAfterRevision();
      ContentRevision rightRevision = rightChange.getBeforeRevision();

      String leftTitle = getRevisionTitle(myContext, DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE, leftRevision,
                                          ChangeDiffRequestProducer.getYourVersion());
      String centerTitle = getRevisionTitle(myContext, DiffUserDataKeysEx.VCS_DIFF_CENTER_CONTENT_TITLE, centerRevision,
                                            ChangeDiffRequestProducer.getMergedVersion());
      String rightTitle = getRevisionTitle(myContext, DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE, rightRevision,
                                           ChangeDiffRequestProducer.getServerVersion());

      if (leftRevision == null) {
        return createTwoSideRequest(project, centerRevision, rightRevision, requestTitle, centerTitle, rightTitle, context, indicator);
      }
      else if (rightRevision == null) {
        return createTwoSideRequest(project, leftRevision, centerRevision, requestTitle, leftTitle, centerTitle, context, indicator);
      }
      else if (centerRevision == null) {
        return createTwoSideRequest(project, leftRevision, rightRevision, requestTitle, leftTitle, rightTitle, context, indicator);
      }
      return new SimpleDiffRequest(requestTitle,
                                   ChangeDiffRequestProducer.createContent(project, leftRevision, context, indicator),
                                   ChangeDiffRequestProducer.createContent(project, centerRevision, context, indicator),
                                   ChangeDiffRequestProducer.createContent(project, rightRevision, context, indicator),
                                   leftTitle, centerTitle, rightTitle);
    }

    @Override
    public @NotNull String getName() {
      return getRequestTitle(myMergedChange);
    }

    @Override
    public @NotNull FilePath getFilePath() {
      return ChangesUtil.getFilePath(myMergedChange);
    }

    @Override
    public @NotNull FileStatus getFileStatus() {
      return myMergedChange.getFileStatus();
    }
  }
}