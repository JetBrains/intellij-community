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
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.SIMILAR_USAGES_PREVIEW_TOOLBAR;

public class MostCommonUsagePatternsComponent extends SimpleToolWindowPanel implements Disposable {
  private static final int CLUSTER_LIMIT = 20;
  private final @NotNull Project myProject;
  private final @NotNull UsageViewImpl myUsageView;
  private final @NotNull JBPanelWithEmptyText myMainPanel;
  private final @NotNull JScrollPane myScrollPane;
  private final @NotNull SimpleColoredComponent myResultsText;
  private @NotNull Set<Usage> mySelectedUsages;
  private @Nullable ClusteringSearchSession mySession;
  private boolean myIsShowingSimilarUsages;
  private boolean isDisposed = false;
  private @Nullable BackgroundableProcessIndicator myProcessIndicator;
  private int myAlreadyRenderedSnippets;

  public MostCommonUsagePatternsComponent(@NotNull UsageViewImpl usageView) {
    super(true);
    myUsageView = usageView;
    myResultsText = new SimpleColoredComponent();
    myProject = usageView.getProject();
    mySelectedUsages = myUsageView.getSelectedUsages();
    setToolbar(createMostCommonUsagesToolbar());
    myMainPanel = new JBPanelWithEmptyText();
    myMainPanel.setLayout(new VerticalLayout(0));
    myMainPanel.setBackground(UIUtil.getTextFieldBackground());
    addMostCommonUsagesForSelectedGroups();
    myScrollPane = ScrollPaneFactory.createScrollPane(myMainPanel, true);
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

  public JComponent createMostCommonUsagesToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(
      new RefreshAction(IdeBundle.messagePointer("action.refresh"), IdeBundle.messagePointer("action.refresh"), AllIcons.Actions.Refresh) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myMainPanel.removeAll();
          myMainPanel.revalidate();
          myIsShowingSimilarUsages = false;
          mySelectedUsages = myUsageView.getSelectedUsages();
          updateResultsText(UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()));
          addMostCommonUsagesForSelectedGroups();
          setContent(myScrollPane);
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
            updateResultsText(UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()));
            ActivityTracker.getInstance().inc();
            setContent(myScrollPane);
            revalidate();
          }
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
          Presentation presentation = event.getPresentation();
          presentation.setVisible(myIsShowingSimilarUsages);
        }
      });
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(SIMILAR_USAGES_PREVIEW_TOOLBAR, actionGroup, true);
    actionToolbar.getComponent().setBackground(UIUtil.getTextFieldBackground());
    actionToolbar.setTargetComponent(this);
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    toolbar.setBackground(UIUtil.getTextFieldBackground());
    updateResultsText(UsageViewBundle.message("similar.usages.0.results", mySelectedUsages.size()));
    toolbar.add(myResultsText);
    toolbar.add(actionToolbar.getComponent());
    return toolbar;
  }

  private void updateResultsText(@NotNull @Nls String message) {
    myResultsText.clear();
    myResultsText.append(message, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  private @NotNull ActionLink createOpenSimilarUsagesActionLink(@NotNull UsageInfo info, @NotNull Set<SimilarUsage> usagesToRender) {
    final ActionLink actionLink =
      new ActionLink(UsageViewBundle.message("similar.usages.show.0.similar.usages.title", usagesToRender.size() - 1), e -> {
        myIsShowingSimilarUsages = true;
        updateResultsText(UsageViewBundle.message("0.similar.usages", usagesToRender.size() - 1));
      ActivityTracker.getInstance().inc();
      final SimilarUsagesComponent similarComponent = new SimilarUsagesComponent(info, this);
      JScrollPane similarUsagesScrollPane = ScrollPaneFactory.createScrollPane(similarComponent, true);
      final JScrollBar scrollBar = similarUsagesScrollPane.getVerticalScrollBar();
      similarComponent.renderOriginalUsage();
      similarComponent.renderSimilarUsages(usagesToRender);
      BoundedRangeModelThresholdListener.install(scrollBar, () -> {
        similarComponent.renderSimilarUsages(usagesToRender);
        return Unit.INSTANCE;
      });
      setContent(similarUsagesScrollPane);
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
                                                 @NotNull Collection<UsageCluster> clusterToShow) {
    JPanel summaryPanel = new JPanel(new VerticalLayout(0));
    if (session == null) return summaryPanel;
    clusterToShow.stream().limit(CLUSTER_LIMIT).forEach(cluster -> {
      renderClusterDescription(summaryPanel, cluster.getUsages());
    });
    final JScrollBar verticalScrollBar = myScrollPane.getVerticalScrollBar();
    BoundedRangeModelThresholdListener.install(verticalScrollBar, () -> {
      clusterToShow.stream().skip(myAlreadyRenderedSnippets).limit(CLUSTER_LIMIT).forEach(cluster -> {
        renderClusterDescription(summaryPanel, cluster.getUsages());
      });
      return Unit.INSTANCE;
    });
    return summaryPanel;
  }

  private void renderClusterDescription(@NotNull JPanel summaryPanel, @NotNull Collection<SimilarUsage> selectedUsages) {
    final Set<SimilarUsage> usageFilteredByGroup = new HashSet<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      usageFilteredByGroup.addAll(selectedUsages);
    });
    SimilarUsage usage = ContainerUtil.getFirstItem(usageFilteredByGroup);
    if (usage instanceof UsageInfo2UsageAdapter) {
      final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
      final PsiElement element = Objects.requireNonNull(usageInfo.getElement());
      UsageCodeSnippetComponent summaryRendererComponent = new UsageCodeSnippetComponent(element);
      if (!isDisposed) {
        Disposer.register(this, summaryRendererComponent);
      }
      final JPanel headerPanel = createHeaderPanel(usageInfo, summaryRendererComponent.getEditor().getBackgroundColor());
      if (usageFilteredByGroup.size() > 1) {
        headerPanel.add(createOpenSimilarUsagesActionLink(usageInfo, usageFilteredByGroup));
      }
      summaryPanel.add(headerPanel);
      summaryPanel.add(summaryRendererComponent);
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
            myMainPanel.add(createSummaryComponent(getSession(), sortedClusters.get()));
            myMainPanel.revalidate();
            myIsShowingSimilarUsages = false;
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
    Optional<Usage> usage = usageView.getUsages().stream().findFirst();
    if (usage.isPresent()) {
      Usage firstUsage = usage.get();
      if (firstUsage instanceof SimilarUsage) {
        return ((SimilarUsage)firstUsage).getClusteringSession();
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
