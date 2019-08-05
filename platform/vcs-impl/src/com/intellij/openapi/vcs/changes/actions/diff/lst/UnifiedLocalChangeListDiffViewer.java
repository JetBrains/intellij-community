// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.fragmented.UnifiedDiffChange;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.fragmented.UnifiedFragmentBuilder;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UnifiedLocalChangeListDiffViewer extends UnifiedDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;

  private final boolean myAllowExcludeChangesFromCommit;

  public UnifiedLocalChangeListDiffViewer(@NotNull DiffContext context,
                                          @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);
  }

  @NotNull
  private Runnable superComputeDifferences(@NotNull ProgressIndicator indicator) {
    return super.computeDifferences(indicator);
  }

  @NotNull
  @Override
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    Document document1 = getContent1().getDocument();
    Document document2 = getContent2().getDocument();

    return LocalTrackerDiffUtil.computeDifferences(
      myLocalRequest.getLineStatusTracker(),
      document1,
      document2,
      myLocalRequest.getChangelistId(),
      myTextDiffProvider,
      indicator,
      new MyLocalTrackerDiffHandler(document1, document2, indicator));
  }

  private class MyLocalTrackerDiffHandler implements LocalTrackerDiffUtil.LocalTrackerDiffHandler {
    @NotNull private final Document myDocument1;
    @NotNull private final Document myDocument2;
    @NotNull private final ProgressIndicator myIndicator;

    private MyLocalTrackerDiffHandler(@NotNull Document document1,
                                      @NotNull Document document2,
                                      @NotNull ProgressIndicator indicator) {
      myDocument1 = document1;
      myDocument2 = document2;
      myIndicator = indicator;
    }

    @NotNull
    @Override
    public Runnable done(boolean isContentsEqual,
                         @NotNull CharSequence[] texts,
                         @NotNull List<? extends LineFragment> fragments,
                         @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData) {
      UnifiedFragmentBuilder builder = ReadAction.compute(() -> {
        myIndicator.checkCanceled();
        return new MyUnifiedFragmentBuilder(fragments, fragmentsData, myDocument1, myDocument2).exec();
      });

      return apply(builder, texts, myIndicator);
    }

    @NotNull
    @Override
    public Runnable retryLater() {
      scheduleRediff();
      throw new ProcessCanceledException();
    }

    @NotNull
    @Override
    public Runnable fallback() {
      return superComputeDifferences(myIndicator);
    }

    @NotNull
    @Override
    public Runnable fallbackWithProgress() {
      Runnable callback = superComputeDifferences(myIndicator);
      return () -> {
        callback.run();
        getStatusPanel().setBusy(true);
      };
    }

    @NotNull
    @Override
    public Runnable error() {
      return applyErrorNotification();
    }
  }

  private class MyUnifiedFragmentBuilder extends UnifiedFragmentBuilder {
    @NotNull private final List<LocalTrackerDiffUtil.LineFragmentData> myFragmentsData;

    MyUnifiedFragmentBuilder(@NotNull List<? extends LineFragment> fragments,
                             @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData,
                             @NotNull Document document1,
                             @NotNull Document document2) {
      super(fragments, document1, document2, myMasterSide);
      myFragmentsData = fragmentsData;
    }

    @NotNull
    @Override
    protected UnifiedDiffChange createDiffChange(int blockStart,
                                                 int insertedStart,
                                                 int blockEnd,
                                                 int fragmentIndex) {
      LineFragment fragment = myFragments.get(fragmentIndex);
      LocalTrackerDiffUtil.LineFragmentData data = myFragmentsData.get(fragmentIndex);
      boolean isSkipped = data.isSkipped();
      boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);
      return new UnifiedDiffChange(blockStart, insertedStart, blockEnd, fragment, isExcluded, isSkipped);
    }
  }
}
