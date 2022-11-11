// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.findUsages.similarity.SimilarUsagesComponent;
import com.intellij.find.findUsages.similarity.SimilarUsagesToolbar;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.components.AnActionLink;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.openapi.actionSystem.ActionPlaces.SIMILAR_USAGES_PREVIEW_TOOLBAR;

public class UsagePreviewToolbarWithSimilarUsagesLink extends JPanel {
  private final UsageView myUsageView;

  public UsagePreviewToolbarWithSimilarUsagesLink(@NotNull UsagePreviewPanel previewPanel,
                                                  @NotNull UsageView usageView,
                                                  @NotNull List<? extends UsageInfo> selectedInfos,
                                                  @NotNull UsageCluster cluster) {
    super(new FlowLayout(FlowLayout.LEFT));
    myUsageView = usageView;
    setBackground(UIUtil.getTextFieldBackground());
    add(createSimilarUsagesLink(previewPanel, selectedInfos, cluster.getUsages()));
    add(createRefreshButton(previewPanel, usageView, selectedInfos));
  }

  @NotNull
  private static JComponent createRefreshButton(@NotNull UsagePreviewPanel previewPanel,
                                                @NotNull UsageView usageView,
                                                @NotNull List<? extends UsageInfo> selectedInfos) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RefreshAction(IdeBundle.messagePointer("action.refresh"),
                                IdeBundle.messagePointer("action.refresh"),
                                AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        previewPanel.updateLayout(selectedInfos, usageView);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(true);
      }
    });
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(SIMILAR_USAGES_PREVIEW_TOOLBAR, group, true);
    actionToolbar.setTargetComponent(previewPanel);
    JComponent component = actionToolbar.getComponent();
    component.setBackground(UIUtil.getTextFieldBackground());
    return component;
  }

  private @NotNull AnActionLink createSimilarUsagesLink(@NotNull UsagePreviewPanel previewPanel,
                                                        @NotNull List<? extends UsageInfo> infos,
                                                        @NotNull Set<SimilarUsage> usages) {
    AnActionLink similarUsagesLink = new AnActionLink(
      UsageViewBundle.message("similar.usages.show.0.similar.usages.title", usages.size() - 1), new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Set<SimilarUsage> onlyValidUsages = usages.stream().filter(usage -> usage.isValid()).collect(Collectors.toSet());
        previewPanel.removeAll();
        previewPanel.revalidate();
        previewPanel.releaseEditor();
        UsageInfo firstSelectedInfo = ContainerUtil.getFirstItem(infos);
        assert firstSelectedInfo != null;
        SimilarUsagesCollector.logLinkToSimilarUsagesLinkFromUsagePreviewClicked(firstSelectedInfo.getProject(), myUsageView);
        final SimilarUsagesComponent similarComponent =
          new SimilarUsagesComponent(myUsageView, firstSelectedInfo, previewPanel);
        previewPanel.add(
          new SimilarUsagesToolbar(similarComponent, UsageViewBundle.message("0.similar.usages", onlyValidUsages.size() - 1), null,
                                   new AnActionLink(UsageViewBundle.message("0.similar.usages.back.to.usage.preview", UIUtil.leftArrow()),
                                                    new AnAction() {
                                                      @Override
                                                      public void actionPerformed(@NotNull AnActionEvent e) {
                                                        previewPanel.removeAll();
                                                        previewPanel.updateLayout(infos, myUsageView);
                                                      }
                                                    })), BorderLayout.NORTH);
        previewPanel.add(similarComponent.createLazyLoadingScrollPane(onlyValidUsages));
      }
    });
    similarUsagesLink.setVisible(usages.size() > 1);
    return similarUsagesLink;
  }
}
