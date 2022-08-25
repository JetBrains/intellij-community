// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
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
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.usageAdapter.SimilarityUsage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.SIMILAR_USAGES_PREVIEW_TOOLBAR;

public class MostCommonUsagePatternsComponent extends SimpleToolWindowPanel implements Disposable {
  private static final int CLUSTER_LIMIT = 20;
  private final @NotNull Project myProject;
  private final @NotNull Collection<Collection<? extends UsageGroup>> myGroups;
  private final @NotNull UsageViewImpl myUsageView;
  private final @NotNull JBPanelWithEmptyText myMainPanel;
  private final @NotNull JScrollPane myScrollPane;
  private @Nullable ClusteringSearchSession mySession;
  private boolean myIsShowingSimilarUsages;

  private boolean isDisposed = false;
  private @Nullable BackgroundableProcessIndicator myProcessIndicator;
  private int myAlreadyRenderedSnippets;

  public MostCommonUsagePatternsComponent(@NotNull UsageViewImpl usageView,
                                          @NotNull Collection<@NotNull Collection<? extends UsageGroup>> groups) {
    super(false);
    myUsageView = usageView;
    myProject = usageView.getProject();
    myGroups = groups;
    setToolbar((JComponent)createToolbar(SIMILAR_USAGES_PREVIEW_TOOLBAR));
    myMainPanel = new JBPanelWithEmptyText();
    myMainPanel.setLayout(new VerticalLayout(0));
    addMostCommonUsagesForSelectedGroups(myGroups);
    myScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    revalidate();
    setContent(myScrollPane);
  }

  @Nullable
  public ClusteringSearchSession getSession() {
    if (mySession == null) {
      mySession = findClusteringSessionInUsageView(myUsageView);
    }
    return mySession;
  }

  public ActionToolbar createToolbar(@NotNull String place) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(
      new RefreshAction(IdeBundle.messagePointer("action.refresh"), IdeBundle.messagePointer("action.refresh"), AllIcons.Actions.Refresh) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myMainPanel.removeAll();
          myMainPanel.revalidate();
          myIsShowingSimilarUsages = false;
          addMostCommonUsagesForSelectedGroups(myGroups);
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
          Presentation presentation = event.getPresentation();
          presentation.setEnabled(true);
        }
      });
    actionGroup.add(
      new AnAction(IdeBundle.messagePointer("action.Anonymous.text.back"), IdeBundle.messagePointer("action.Anonymous.text.back"),
                   AllIcons.Actions.Back) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (myIsShowingSimilarUsages) {
            myIsShowingSimilarUsages = false;
            ActivityTracker.getInstance().inc();
            setContent(myScrollPane);
            revalidate();
          }
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
          Presentation presentation = event.getPresentation();
          presentation.setEnabled(myIsShowingSimilarUsages);
        }
      });
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, false);
    actionToolbar.setTargetComponent(myMainPanel);
    return actionToolbar;
  }

  private @NotNull ActionLink createOpenSimilarUsagesActionLink(UsageInfo info, @NotNull Set<SimilarityUsage> usagesToRender) {
    final ActionLink actionLink = new ActionLink(UsageViewBundle.message("show.0.similar.usages", usagesToRender.size() - 1), e -> {
      myIsShowingSimilarUsages = true;
      ActivityTracker.getInstance().inc();
      final SimilarUsagesComponent mySimilarComponent = new SimilarUsagesComponent(info, this);
      JScrollPane scroll = ScrollPaneFactory.createScrollPane(mySimilarComponent);
      final JScrollBar scrollBar = scroll.getVerticalScrollBar();
      mySimilarComponent.renderOriginalUsage();
      mySimilarComponent.renderSimilarUsages(usagesToRender);
      BoundedRangeModelThresholdListener.Companion.install(scrollBar, () -> {
        mySimilarComponent.renderSimilarUsages(usagesToRender);
        return Unit.INSTANCE;
      });
      setContent(scroll);
      revalidate();
    });
    actionLink.setLinkIcon();
    return actionLink;
  }

  @Override
  public void dispose() {
    isDisposed = true;
    if (myProcessIndicator != null && myProcessIndicator.isRunning()) {
      myProcessIndicator.cancel();
    }
  }

  private @NotNull JPanel createSummaryComponent(@Nullable ClusteringSearchSession session,
                                                 @NotNull List<UsageCluster> topClusters) {
    JPanel summaryPanel = new JPanel(new VerticalLayout(0));
    if (session == null) return summaryPanel;
    myAlreadyRenderedSnippets = 0;
    topClusters.stream().skip(myAlreadyRenderedSnippets).limit(CLUSTER_LIMIT).forEach(cluster -> {
      renderCluster(summaryPanel, cluster);
    });
    final JScrollBar verticalScrollBar = myScrollPane.getVerticalScrollBar();
    BoundedRangeModelThresholdListener.Companion.install(verticalScrollBar, () -> {
      topClusters.stream().skip(myAlreadyRenderedSnippets).limit(CLUSTER_LIMIT).forEach(cluster -> {
        renderCluster(summaryPanel, cluster);
      });
      return Unit.INSTANCE;
    });
    return summaryPanel;
  }

  private void renderCluster(@NotNull JPanel summaryPanel,
                             @NotNull UsageCluster cluster) {
    final Set<SimilarityUsage> usageFilteredByGroup = new HashSet<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      usageFilteredByGroup.addAll(cluster.getUsageFilteredByGroup(myGroups));
    });
    SimilarityUsage usage = ContainerUtil.getFirstItem(usageFilteredByGroup);
    if (usage instanceof UsageInfo2UsageAdapter) {
      final UsageInfo2UsageAdapter usageInfoAdapter = (UsageInfo2UsageAdapter)usage;
      UsageCodeSnippetComponent summaryRendererComponent =
        UsageCodeSnippetComponent.createUsageCodeSnippet(usageInfoAdapter.getUsageInfo());
      if (!isDisposed) {
        Disposer.register(this, summaryRendererComponent);
      }
      final JPanel headerPanel =
        createHeaderPanel(usageInfoAdapter.getUsageInfo(), summaryRendererComponent.getEditor().getBackgroundColor());
      if (usageFilteredByGroup.size() > 1) {
        headerPanel.add(createOpenSimilarUsagesActionLink(usageInfoAdapter.getUsageInfo(), usageFilteredByGroup));
      }
      summaryPanel.add(headerPanel);
      summaryPanel.add(summaryRendererComponent);
      myAlreadyRenderedSnippets++;
    }
  }

  private void addMostCommonUsagesForSelectedGroups(@NotNull Collection<Collection<? extends UsageGroup>> selectedGroups) {
    Ref<List<UsageCluster>> sortedClusters = new Ref<>();
    Task.Backgroundable loadMostCommonUsagePatternsTask =
      new Task.Backgroundable(myProject, UsageViewBundle.message("loading.most.common.usage.patterns")) {
        @Override
        public void onSuccess() {
          if (!sortedClusters.isNull()) {
            myMainPanel.add(createSummaryComponent(getSession(), sortedClusters.get()));
            myMainPanel.revalidate();
            myIsShowingSimilarUsages = false;
          }
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ClusteringSearchSession session = getSession();
          if (session != null) {
            final List<UsageCluster> groups = new ArrayList<>();
            ApplicationManager.getApplication().runReadAction(() -> {
              groups.addAll(session.getClustersFilteredByGroups(indicator, selectedGroups));
            });
            sortedClusters.set(groups);
          }
        }
      };
    myProcessIndicator = new BackgroundableProcessIndicator(loadMostCommonUsagePatternsTask);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(loadMostCommonUsagePatternsTask,
                                                                       myProcessIndicator);
  }

  public static @Nullable ClusteringSearchSession findClusteringSessionInUsageView(@NotNull UsageView usageView) {
    Optional<Usage> usage = usageView.getUsages().stream().findFirst();
    if (usage.isPresent()) {
      Usage firstUsage = usage.get();
      if (firstUsage instanceof SimilarityUsage) {
        return ((SimilarityUsage)firstUsage).getClusteringSession();
      }
    }
    return null;
  }

  public static @NotNull JPanel createHeaderPanel(@NotNull UsageInfo info, @NotNull Color backgroundColor) {
    final LocationLinkComponent component = new LocationLinkComponent(info);
    final JPanel header = new JPanel();
    header.setBackground(backgroundColor);
    header.setLayout(new FlowLayout(FlowLayout.LEFT));
    header.add(component.getComponent());
    header.setBorder(JBUI.Borders.customLineTop(new JBColor(Gray.xCD, Gray.x51)));
    return header;
  }
}
