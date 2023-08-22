// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.find.FindManager;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class SearchCommand {
  @NotNull
  protected final SearchContext mySearchContext;
  @NotNull
  protected final Configuration myConfiguration;
  private FindUsagesProcessPresentation myProcessPresentation;

  public SearchCommand(@NotNull Configuration configuration, @NotNull SearchContext searchContext) {
    myConfiguration = configuration;
    mySearchContext = searchContext;
  }

  @NotNull
  protected UsageViewContext createUsageViewContext() {
    final Runnable searchStarter = () -> new SearchCommand(myConfiguration, mySearchContext).startSearching();
    return new UsageViewContext(myConfiguration, mySearchContext, searchStarter);
  }

  public void startSearching() {
    final UsageViewContext context = createUsageViewContext();
    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setOpenInNewTab(FindSettings.getInstance().isShowResultsInSeparateView());
    context.configure(presentation);

    myProcessPresentation = new FindUsagesProcessPresentation(presentation);
    myProcessPresentation.setShowNotFoundMessage(true);
    myProcessPresentation.setShowPanelIfOnlyOneUsage(true);

    PsiDocumentManager.getInstance(mySearchContext.getProject()).commitAllDocuments();
    final ConfigurableUsageTarget target = context.getTarget();
    ((FindManagerImpl)FindManager.getInstance(mySearchContext.getProject())).getFindUsagesManager().addToHistory(target);
    UsageViewManager.getInstance(mySearchContext.getProject()).searchAndShowUsages(
      new UsageTarget[]{target},
      () -> processor -> findUsages(processor),
      myProcessPresentation,
      presentation,
      new UsageViewManager.UsageViewStateListener() {
        @Override
        public void usageViewCreated(@NotNull UsageView usageView) {
          context.setUsageView(usageView);
          context.configureActions();
        }

        @Override
        public void findingUsagesFinished(final UsageView usageView) {}
      }
    );
  }

  public void findUsages(@NotNull Processor<? super Usage> processor) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progress.setIndeterminate(false);

    final MatchResultSink sink = new MatchResultSink() {
      int count;

      @Override
      public void setMatchingProcess(@NotNull MatchingProcess _process) {
        findStarted();
      }

      @Override
      public void processFile(@NotNull PsiFile element) {
        final VirtualFile virtualFile = element.getVirtualFile();
        if (virtualFile != null)
          progress.setText(SSRBundle.message("looking.in.progress.message", virtualFile.getPresentableName()));
      }

      @Override
      public void matchingFinished() {
        if (mySearchContext.getProject().isDisposed()) return;
        findEnded();
        progress.setText(SSRBundle.message("found.progress.message", count));
      }

      @Override
      public ProgressIndicator getProgressIndicator() {
        return progress;
      }

      @Override
      public void newMatch(@NotNull MatchResult result) {
        UsageInfo info;

        if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
          int start = -1;
          int end = -1;
          PsiElement parent = result.getMatch().getParent();

          for (final MatchResult matchResult : result.getChildren()) {
            PsiElement el = matchResult.getMatch();
            final int elementStart = el.getTextRange().getStartOffset();

            if (start == -1 || start > elementStart) {
              start = elementStart;
            }
            final int newend = elementStart + el.getTextLength();

            if (newend > end) {
              end = newend;
            }
          }

          final int parentStart = parent.getTextRange().getStartOffset();
          int startOffset = start - parentStart;
          info = new UsageInfo(parent, startOffset, end - parentStart);
        }
        else {
          final PsiElement match = result.getMatch();
          if (!match.isPhysical()) {
            // e.g. lambda parameter anonymous type element
            return;
          }
          info = new UsageInfo(match);
        }

        final Usage usage = new UsageInfo2UsageAdapter(info);
        foundUsage(result, usage);
        processor.process(usage);
        ++count;
      }
    };

    try {
      new Matcher(mySearchContext.getProject(), myConfiguration.getMatchOptions()).findMatches(sink);
    }
    catch (StructuralSearchException e) {
      myProcessPresentation.setShowNotFoundMessage(false);
      @SuppressWarnings("InstanceofCatchParameter") String content =
        e instanceof StructuralSearchScriptException
        ? SSRBundle.message("search.script.problem", e.getCause().toString().replace(ScriptSupport.UUID, ""))
        : SSRBundle.message("search.template.problem", e.getMessage());
      NotificationGroupManager.getInstance()
        .getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID)
        .createNotification(content, NotificationType.ERROR)
        .setImportant(true)
        .notify(mySearchContext.getProject());
    }
  }

  protected void findStarted() {}

  protected void findEnded() {}

  protected void foundUsage(MatchResult result, Usage usage) {}
}
