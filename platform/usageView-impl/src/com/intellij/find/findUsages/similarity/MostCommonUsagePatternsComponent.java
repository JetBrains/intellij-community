// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class MostCommonUsagePatternsComponent extends SimpleToolWindowPanel implements Disposable {
  private static final int CLUSTER_LIMIT = 3;
  private final @NotNull Project myProject;
  private final @NotNull UsageViewImpl myUsageView;
  private final @NotNull JBPanelWithEmptyText myMainPanel;
  private final @NotNull JScrollPane myMostCommonUsageScrollPane;
  private final @NotNull Ref<Collection<UsageCluster>> mySortedClusters;
  private @NotNull MostCommonUsagesToolbar myMostCommonUsagesToolbar;
  private final @NotNull RefreshAction myRefreshAction;
  private @NotNull Set<Usage> mySelectedUsages;
  private final @NotNull Set<Usage> myNonClusteredUsages;
  private final @NotNull ClusteringSearchSession mySession;
  private int myAlreadyRenderedSnippets;
  private final AtomicBoolean isRefreshing;
  private final AtomicBoolean isShowingSimilarUsagesComponent;
  private final AtomicInteger lastUsagesNumber;
  private boolean isDisposed;

  public MostCommonUsagePatternsComponent(@NotNull UsageViewImpl usageView, @NotNull ClusteringSearchSession session) {
    super(true);
    isRefreshing = new AtomicBoolean(false);
    isShowingSimilarUsagesComponent = new AtomicBoolean(false);
    lastUsagesNumber = new AtomicInteger(0);
    if (Registry.is("similarity.find.usages.view.auto.update")) {
      ScheduledFuture<?> fireEventsFuture =
        EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::refreshIfNeeded, 1000, 1000, TimeUnit.MILLISECONDS);
      Disposer.register(this, () -> fireEventsFuture.cancel(true));
    }

    mySession = session;
    myUsageView = usageView;
    myProject = usageView.getProject();
    SimilarUsagesCollector.logMostCommonUsagePatternsShow(myProject, myUsageView);
    mySortedClusters = new Ref<>(null);
    mySelectedUsages = myUsageView.getSelectedUsages();
    myNonClusteredUsages = mySelectedUsages.stream().filter(e -> !(e instanceof SimilarUsage)).collect(Collectors.toCollection(HashSet::new));
    myMainPanel = new JBPanelWithEmptyText();
    myMainPanel.setLayout(new VerticalLayout(0));
    myMainPanel.setBackground(UIUtil.getTextFieldBackground());
    myMostCommonUsageScrollPane = createLazyLoadingScrollPane();
    myRefreshAction =
      new RefreshAction(IdeBundle.messagePointer("action.refresh"), IdeBundle.messagePointer("action.refresh"), AllIcons.Actions.Refresh) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          SimilarUsagesCollector.logMostCommonUsagePatternsRefreshClicked(myProject, myUsageView);
          refresh();
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
          event.getPresentation().setEnabled(true);
        }
      };
    myMostCommonUsagesToolbar =
      new MostCommonUsagesToolbar(this, UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()), myRefreshAction);
    addInternalClusteringSessionActions(usageView);
    setToolbar(myMostCommonUsagesToolbar);
    addMostCommonUsagesForSelectedGroups();
    revalidate();
    setContent(myMostCommonUsageScrollPane);
  }

  private void refreshIfNeeded() {
    if (refreshNeeded()) {
      refresh();
    }
  }

  private boolean refreshNeeded() {
    return !isScrolled() &&
           !isRefreshing.get() && !isShowingSimilarUsagesComponent.get() &&
           (myUsageView.isSearchInProgress() || mySelectedUsages.size() != myUsageView.getSelectedUsages().size());
  }

  private boolean isScrolled() {
    return myMostCommonUsageScrollPane.getVerticalScrollBar().getValue() != 0;
  }

  private void refresh() {
    isRefreshing.set(true);
    mySortedClusters.set(null);
    mySelectedUsages = myUsageView.getSelectedUsages();
    addMostCommonUsagesForSelectedGroups();
  }

  private void updateToolbar() {
    setToolbar(null);
    myMostCommonUsagesToolbar = new MostCommonUsagesToolbar(myMostCommonUsageScrollPane,
                                                            UsageViewBundle.message("similar.usages.0.results",
                                                                                    mySelectedUsages.size()),
                                                            myRefreshAction);
    setToolbar(myMostCommonUsagesToolbar);
    addInternalClusteringSessionActions(myUsageView);
    setContent(myMostCommonUsageScrollPane);
  }


  @NotNull
  private JScrollPane createLazyLoadingScrollPane() {
    JScrollPane lazyLoadingScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel, true);
    lazyLoadingScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    BoundedRangeModelThresholdListener.install(lazyLoadingScrollPane.getVerticalScrollBar(), () -> {
      loadMoreSnippets(lazyLoadingScrollPane.getVerticalScrollBar().getValue() != 0);
      return Unit.INSTANCE;
    });
    return lazyLoadingScrollPane;
  }

  private void loadMoreSnippets(boolean logMoreSnippetsLoaded) {
    if (mySortedClusters.isNull()) return;
    Collection<UsageCluster> sortedClusters = mySortedClusters.get();
    if (myAlreadyRenderedSnippets < sortedClusters.size()) {
      sortedClusters.stream().skip(myAlreadyRenderedSnippets).limit(CLUSTER_LIMIT).forEach(cluster -> {
        final Set<SimilarUsage> filteredUsages =
          cluster.getUsages().stream().filter(e -> (e instanceof UsageInfo2UsageAdapter)).collect(Collectors.toSet());
        renderClusterDescription(ContainerUtil.getFirstItem(filteredUsages), filteredUsages);
      });
      if (logMoreSnippetsLoaded) {
        SimilarUsagesCollector.logMoreClustersLoaded(myProject, myUsageView, myAlreadyRenderedSnippets);
      }
    }
    if (myAlreadyRenderedSnippets >= sortedClusters.size()) {
      int numberOfAlreadyRenderedNonClusteredUsages = myAlreadyRenderedSnippets - sortedClusters.size();
      if (numberOfAlreadyRenderedNonClusteredUsages < myNonClusteredUsages.size()) {
        myNonClusteredUsages.stream().skip(numberOfAlreadyRenderedNonClusteredUsages).limit(CLUSTER_LIMIT).forEach(e -> {
          if (e instanceof UsageInfo2UsageAdapter && e.isValid()) {
            renderNonClusteredUsage((UsageInfo2UsageAdapter)e);
          }
        });
        if (logMoreSnippetsLoaded) {
          SimilarUsagesCollector.logMoreNonClusteredUsagesLoaded(myProject, myUsageView, myAlreadyRenderedSnippets);
        }
      }
    }
    myMainPanel.revalidate();
    myMainPanel.repaint();
  }

  private void addInternalClusteringSessionActions(@NotNull UsageViewImpl usageView) {
    if (Registry.is("similarity.import.clustering.results.action.enabled")) {
      myMostCommonUsagesToolbar.add(new ExportClusteringResultActionLink(myProject, mySession,
                                                                         StringUtilRt.notNullize(usageView.getTargets()[0].getName(),
                                                                                                 "features")));
      myMostCommonUsagesToolbar.add(new ImportClusteringResultActionLink(myProject, mySession, myRefreshAction));
    }
  }


  private @NotNull ActionLink createOpenSimilarUsagesActionLink(@NotNull UsageInfo info, @NotNull Set<SimilarUsage> usagesToRender) {
    final ActionLink actionLink =
      new ActionLink(UsageViewBundle.message("similar.usages.show.0.similar.usages.title", usagesToRender.size() - 1), e -> {
        SimilarUsagesCollector.logShowSimilarUsagesLinkClicked(myProject, myUsageView);
        final SimilarUsagesComponent similarComponent = new SimilarUsagesComponent(myUsageView, info, this);
        removeAll();
        isShowingSimilarUsagesComponent.set(true);
        setToolbar(new SimilarUsagesToolbar(similarComponent, UsageViewBundle.message("0.similar.usages", usagesToRender.size() - 1),
                                            myRefreshAction,
                                            new ActionLink(
                                              UsageViewBundle.message("0.similar.usages.back.to.search.results", UIUtil.leftArrow()),
                                              event -> {
                                                removeAll();
                                                setToolbar(myMostCommonUsagesToolbar);
                                                setContent(myMostCommonUsageScrollPane);
                                                revalidate();
                                                isShowingSimilarUsagesComponent.set(false);
                                              }
                                            )));
        setContent(similarComponent.createLazyLoadingScrollPane(usagesToRender));
        revalidate();
      });
    actionLink.setLinkIcon();
    return actionLink;
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  private void renderClusterDescription(@Nullable SimilarUsage usage, @NotNull Set<@NotNull SimilarUsage> clusterUsages) {
    if (usage instanceof UsageInfo2UsageAdapter) {
      final UsageInfo usageInfo = usage.getUsageInfo();
      PsiElement element = usageInfo.getElement();
      if (element != null) {
        UsageCodeSnippetComponent summaryRendererComponent = new UsageCodeSnippetComponent(element);
        Disposer.register(this, summaryRendererComponent);
        myMainPanel.add(createHeaderPanel(usageInfo, clusterUsages));
        myMainPanel.add(summaryRendererComponent);
      }
    }
    myAlreadyRenderedSnippets++;
  }

  private void renderNonClusteredUsage(UsageInfo2UsageAdapter usage) {
    final UsageInfo usageInfo = usage.getUsageInfo();
    UsageCodeSnippetComponent summaryRendererComponent = new UsageCodeSnippetComponent(Objects.requireNonNull(usageInfo.getElement()));
    Disposer.register(this, summaryRendererComponent);
    myMainPanel.add(createHeaderWithLocationLink(usageInfo));
    myMainPanel.add(summaryRendererComponent);
    myAlreadyRenderedSnippets++;
  }

  private void addMostCommonUsagesForSelectedGroups() {
    ReadAction.nonBlocking(() -> {
      mySortedClusters.set(mySession.getClustersForSelectedUsages(mySelectedUsages));
      return true;
    }).finishOnUiThread(
      ModalityState.NON_MODAL, e -> {
        if (!mySortedClusters.isNull() && !isDisposed) {
          myAlreadyRenderedSnippets = 0;
          myMainPanel.removeAll();
          myMainPanel.revalidate();
          updateToolbar();
          loadMoreSnippets(false);
          myMainPanel.revalidate();
          isRefreshing.set(false);
          lastUsagesNumber.set(mySelectedUsages.size());
        }
      }
    ).submit(AppExecutorUtil.getAppExecutorService());
  }

  public static @Nullable ClusteringSearchSession findClusteringSessionInUsageView(@NotNull UsageView usageView) {
    return usageView.getUsages().stream().filter(usage -> usage instanceof SimilarUsage).map(e -> ((SimilarUsage)e).getClusteringSession())
      .findFirst().orElse(null);
  }

  private @NotNull JPanel createHeaderPanel(@NotNull UsageInfo info,
                                            @NotNull Set<SimilarUsage> usageFilteredByGroup) {
    final JPanel header = createHeaderWithLocationLink(info);
    if (usageFilteredByGroup.size() > 1) {
      header.add(createOpenSimilarUsagesActionLink(info, usageFilteredByGroup));
    }
    return header;
  }

  @NotNull
  private static JPanel createHeaderWithLocationLink(@NotNull UsageInfo info) {
    final LocationLinkComponent component = new LocationLinkComponent(info);
    final JPanel header = new JPanel();
    header.setBackground(UIUtil.getTextFieldBackground());
    header.setLayout(new FlowLayout(FlowLayout.LEFT));
    header.add(component.getComponent());
    header.setBorder(JBUI.Borders.customLineTop(new JBColor(Gray.xCD, Gray.x51)));
    return header;
  }
}
