// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
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
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
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


public class MostCommonUsagePatternsComponent extends SimpleToolWindowPanel implements Disposable {
  private static final int CLUSTER_LIMIT = 20;
  private final @NotNull Project myProject;
  private final @NotNull UsageViewImpl myUsageView;
  private final @NotNull JBPanelWithEmptyText myMainPanel;
  private final @NotNull JScrollPane myMostCommonUsageScrollPane;
  private final @NotNull MostCommonUsagesToolbar myMostCommonUsagesToolbar;
  private final @NotNull RefreshAction myRefreshAction;
  private @NotNull Set<Usage> mySelectedUsages;
  private @Nullable ClusteringSearchSession mySession;
  private @Nullable BackgroundableProcessIndicator myProcessIndicator;
  private int myAlreadyRenderedSnippets;

  public MostCommonUsagePatternsComponent(@NotNull UsageViewImpl usageView) {
    super(true);
    myUsageView = usageView;
    myProject = usageView.getProject();
    mySelectedUsages = myUsageView.getSelectedUsages();
    myMainPanel = new JBPanelWithEmptyText();
    myMainPanel.setLayout(new VerticalLayout(0));
    myMainPanel.setBackground(UIUtil.getTextFieldBackground());
    myMostCommonUsageScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel, true);
    myMostCommonUsageScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myRefreshAction =
      new RefreshAction(IdeBundle.messagePointer("action.refresh"), IdeBundle.messagePointer("action.refresh"), AllIcons.Actions.Refresh) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myMainPanel.removeAll();
          myMainPanel.revalidate();
          mySelectedUsages = myUsageView.getSelectedUsages();
          setToolbar(null);
          setToolbar(new MostCommonUsagesToolbar(myMostCommonUsageScrollPane,
                                                 UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()),
                                                 myRefreshAction));
          addMostCommonUsagesForSelectedGroups();
          setContent(myMostCommonUsageScrollPane);
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
          event.getPresentation().setEnabled(true);
        }
      };
    myMostCommonUsagesToolbar = new MostCommonUsagesToolbar(this, UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()), myRefreshAction);
    setToolbar(myMostCommonUsagesToolbar);
    addMostCommonUsagesForSelectedGroups();
    revalidate();
    setContent(myMostCommonUsageScrollPane);
  }

  public  @Nullable ClusteringSearchSession getSession() {
    if (mySession == null) {
      mySession = findClusteringSessionInUsageView(myUsageView);
    }
    return mySession;
  }

  private @NotNull ActionLink createOpenSimilarUsagesActionLink(@NotNull UsageInfo info, @NotNull Set<SimilarUsage> usagesToRender) {
    final ActionLink actionLink =
      new ActionLink(UsageViewBundle.message("similar.usages.show.0.similar.usages.title", usagesToRender.size() - 1), e -> {
        final SimilarUsagesComponent similarComponent = new SimilarUsagesComponent(info, this);
        setToolbar(null);
        setToolbar(new SimilarUsagesToolbar(similarComponent, UsageViewBundle.message("0.similar.usages", usagesToRender.size() - 1),
                                            myRefreshAction,
                                            new ActionLink(
                                              UsageViewBundle.message("0.similar.usages.back.to.search.results", UIUtil.leftArrow()),
                                              event -> {
                                                removeAll();
                                                setToolbar(myMostCommonUsagesToolbar);
                                                setContent(myMostCommonUsageScrollPane);
                                                revalidate();
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
    if (myProcessIndicator != null && myProcessIndicator.isRunning()) {
      myProcessIndicator.cancel();
    }
  }

  private void createSummaryComponent(@Nullable ClusteringSearchSession session, @NotNull Collection<UsageCluster> clustersToShow) {
    if (session == null) return;
    clustersToShow.stream().limit(CLUSTER_LIMIT).forEach(cluster -> {
      renderClusterDescription(cluster.getUsages());
    });
    final JScrollBar verticalScrollBar = myMostCommonUsageScrollPane.getVerticalScrollBar();
    BoundedRangeModelThresholdListener.install(verticalScrollBar, () -> {
      clustersToShow.stream().skip(myAlreadyRenderedSnippets).limit(CLUSTER_LIMIT).forEach(cluster -> {
        renderClusterDescription(cluster.getUsages());
      });
      return Unit.INSTANCE;
    });
  }

  private void renderClusterDescription(@NotNull Collection<SimilarUsage> clusterUsages) {
    final Set<SimilarUsage> usagesFilteredByGroup = new HashSet<>(clusterUsages);
    SimilarUsage usage = ContainerUtil.getFirstItem(usagesFilteredByGroup);
    if (usage instanceof UsageInfo2UsageAdapter) {
      final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
      UsageCodeSnippetComponent summaryRendererComponent = new UsageCodeSnippetComponent(Objects.requireNonNull(usageInfo.getElement()));
      Disposer.register(this, summaryRendererComponent);
      myMainPanel.add(createHeaderPanel(usageInfo, usagesFilteredByGroup));
      myMainPanel.add(summaryRendererComponent);
      myAlreadyRenderedSnippets++;
    }
  }

  private void addMostCommonUsagesForSelectedGroups() {
    Ref<Collection<UsageCluster>> sortedClusters = new Ref<>();
    Task.Backgroundable loadMostCommonUsagePatternsTask =
      new Task.Backgroundable(myProject, UsageViewBundle.message(
        "similar.usages.loading.most.common.usage.patterns.progress.title")) {
        @Override
        public void onSuccess() {
          if (!sortedClusters.isNull()) {
            createSummaryComponent(getSession(), sortedClusters.get());
            myMainPanel.revalidate();
          }
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ClusteringSearchSession session = getSession();
          if (session != null) {
            ApplicationManager.getApplication().runReadAction(() -> {
              sortedClusters.set(session.getClustersForSelectedUsages(indicator, mySelectedUsages));
            });
          }
        }
      };
    myProcessIndicator = new BackgroundableProcessIndicator(loadMostCommonUsagePatternsTask);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(loadMostCommonUsagePatternsTask,
                                                                       myProcessIndicator);
  }

  public static @Nullable ClusteringSearchSession findClusteringSessionInUsageView(@NotNull UsageView usageView) {
    return usageView.getUsages().stream().filter(usage -> usage instanceof SimilarUsage).map(e -> ((SimilarUsage)e).getClusteringSession())
      .findFirst().orElse(null);
  }

  private @NotNull JPanel createHeaderPanel(@NotNull UsageInfo info,
                                            @NotNull Set<SimilarUsage> usageFilteredByGroup) {
    final LocationLinkComponent component = new LocationLinkComponent(info);
    final JPanel header = new JPanel();
    header.setBackground(UIUtil.getTextFieldBackground());
    header.setLayout(new FlowLayout(FlowLayout.LEFT));
    header.add(component.getComponent());
    header.setBorder(JBUI.Borders.customLineTop(new JBColor(Gray.xCD, Gray.x51)));
    if (usageFilteredByGroup.size() > 1) {
      header.add(createOpenSimilarUsagesActionLink(info, usageFilteredByGroup));
    }
    return header;
  }
}
