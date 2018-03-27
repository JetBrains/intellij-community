package com.intellij.structuralsearch.plugin.ui;

import com.intellij.find.FindManager;
import com.intellij.find.FindProgressIndicator;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class SearchCommand {
  protected final SearchContext mySearchContext;
  protected final Configuration myConfiguration;
  private MatchingProcess process;

  public SearchCommand(Configuration configuration, SearchContext searchContext) {
    myConfiguration = configuration;
    mySearchContext = searchContext;
  }

  protected UsageViewContext createUsageViewContext() {
    final Runnable searchStarter = () -> new SearchCommand(myConfiguration, mySearchContext).startSearching();
    return new UsageViewContext(myConfiguration, mySearchContext, searchStarter);
  }

  public void startSearching() {
    final UsageViewContext context = createUsageViewContext();
    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setOpenInNewTab(FindSettings.getInstance().isShowResultsInSeparateView());
    context.configure(presentation);

    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowPanelIfOnlyOneUsage(true);

    processPresentation.setProgressIndicatorFactory(
      new Factory<ProgressIndicator>() {
        @Override
        public ProgressIndicator create() {
          FindProgressIndicator indicator = new FindProgressIndicator(mySearchContext.getProject(), presentation.getScopeText());
          indicator.addStateDelegate(new AbstractProgressIndicatorExBase(){
            @Override
            public void cancel() {
              super.cancel();
              stopAsyncSearch();
            }
          });
          return indicator;
        }
      }
    );

    PsiDocumentManager.getInstance(mySearchContext.getProject()).commitAllDocuments();
    final ConfigurableUsageTarget target = context.getTarget();
    ((FindManagerImpl)FindManager.getInstance(mySearchContext.getProject())).getFindUsagesManager().addToHistory(target);
    UsageViewManager.getInstance(mySearchContext.getProject()).searchAndShowUsages(
      new UsageTarget[]{target},
      () -> new UsageSearcher() {
        @Override
        public void generate(@NotNull final Processor<Usage> processor) {
          findUsages(processor);
        }
      },
      processPresentation,
      presentation,
      new UsageViewManager.UsageViewStateListener() {
        @Override
        public void usageViewCreated(@NotNull UsageView usageView) {
          context.setUsageView(usageView);
          context.configureActions();
        }

        @Override
        public void findingUsagesFinished(final UsageView usageView) {
        }
      }
    );
  }

  public void findUsages(final Processor<Usage> processor) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progress.setIndeterminate(false);

    final MatchResultSink sink = new MatchResultSink() {
      int count;

      public void setMatchingProcess(MatchingProcess _process) {
        process = _process;
        findStarted();
      }

      public void processFile(PsiFile element) {
        final VirtualFile virtualFile = element.getVirtualFile();
        if (virtualFile != null)
          progress.setText(SSRBundle.message("looking.in.progress.message", virtualFile.getPresentableName()));
      }

      public void matchingFinished() {
        if (mySearchContext.getProject().isDisposed()) return;
        findEnded();
        progress.setText(SSRBundle.message("found.progress.message", count));
      }

      public ProgressIndicator getProgressIndicator() {
        return progress;
      }

      public void newMatch(MatchResult result) {
        UsageInfo info;

        if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
          int start = -1;
          int end = -1;
          PsiElement parent = result.getMatch().getParent();

          for (final MatchResult matchResult : ((MatchResultImpl)result).getMatches()) {
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
          final PsiElement match = StructuralSearchUtil.getPresentableElement(result.getMatch());
          if (!match.isPhysical()) {
            // e.g. lambda parameter anonymous type element
            return;
          }
          info = new UsageInfo(match);
        }

        Usage usage = new UsageInfo2UsageAdapter(info);
        processor.process(usage);
        foundUsage(result, usage);
        ++count;
      }
    };

    try {
      new Matcher(mySearchContext.getProject()).findMatches(sink, myConfiguration.getMatchOptions());
    }
    catch (final StructuralSearchException e) {
      final Alarm alarm = new Alarm();
      alarm.addRequest(
        () -> NotificationGroup.toolWindowGroup("Structural Search", ToolWindowId.FIND)
          .createNotification(SSRBundle.message("problem", e.getMessage()), MessageType.ERROR).notify(mySearchContext.getProject()),
        100, ModalityState.NON_MODAL
      );
    }
  }

  public void stopAsyncSearch() {
    if (process!=null) process.stop();
  }

  protected void findStarted() {
    StructuralSearchPlugin.getInstance(mySearchContext.getProject()).setSearchInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(mySearchContext.getProject()).setSearchInProgress(false);
  }

  protected void foundUsage(MatchResult result, Usage usage) {}
}
