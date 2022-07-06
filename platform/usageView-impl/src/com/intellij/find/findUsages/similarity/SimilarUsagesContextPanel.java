// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageContextPanelBase;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.RunnableCallable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;

import static com.intellij.find.findUsages.similarity.MostCommonUsagePatternsComponent.findClusteringSessionInUsageView;

public class SimilarUsagesContextPanel extends UsageContextPanelBase {

  private @NotNull final UsageViewImpl myUsageView;
  private @Nullable ClusteringSearchSession mySession;
  private @Nullable SimilarUsagesComponent mySimilarUsagesComponent;

  public SimilarUsagesContextPanel(@NotNull Project project,
                                   @NotNull UsageViewImpl usageView) {
    super(project, usageView.getPresentation());
    myUsageView = usageView;
  }

  @Override
  public void dispose() {
    super.dispose();
    if (mySimilarUsagesComponent != null) {
      Disposer.dispose(mySimilarUsagesComponent);
    }
    mySimilarUsagesComponent = null;
  }

  @Override
  protected void updateLayoutLater(@Nullable List<? extends UsageInfo> infos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeAll();
    if (mySimilarUsagesComponent != null) {
      Disposer.dispose(mySimilarUsagesComponent);
    }
    JBPanelWithEmptyText mainPanel = new JBPanelWithEmptyText();
    add(mainPanel);
    if (ContainerUtil.isEmpty(infos)) return;
    UsageInfo info = infos.get(0);
    Ref<PsiElement> psiElementRef = new Ref<>();
    ReadAction.nonBlocking(new RunnableCallable(() -> psiElementRef.set(info.getElement()))).finishOnUiThread(
      ModalityState.NON_MODAL, context -> {
        PsiElement psiElement = psiElementRef.get();
        if (psiElement == null) return;
        PsiFile psiFile = psiElement.getContainingFile();
        if (psiFile == null) return;
        ClusteringSearchSession session = findSessionInUsages();
        if (session == null) return;
        final UsageCluster cluster = session.findCluster(info);
        if (cluster == null) return;
        removeAll();
        mainPanel.setLayout(new VerticalLayout(0));
        mySimilarUsagesComponent = new SimilarUsagesComponent(info, this);
        mainPanel.add(mySimilarUsagesComponent.createLazyLoadingScrollPane(new HashSet<SimilarUsage>(cluster.getUsages())));
      }).expireWith(this).submit(AppExecutorUtil.getAppExecutorService());
  }

  private @Nullable ClusteringSearchSession findSessionInUsages() {
    if (mySession == null) {
      mySession = findClusteringSessionInUsageView(myUsageView);
    }
    return mySession;
  }

  public static class Provider implements UsageContextPanel.Provider {

    @Override
    public @NotNull UsageContextPanel create(@NotNull UsageView usageView) {
      return new SimilarUsagesContextPanel(((UsageViewImpl)usageView).getProject(), (UsageViewImpl)usageView);
    }

    @Override
    public boolean isAvailableFor(@NotNull UsageView usageView) {
      if (!ClusteringSearchSession.isSimilarUsagesClusteringEnabled()) return false;
      UsageTarget[] targets = ((UsageViewImpl)usageView).getTargets();
      if (targets.length == 0) return false;
      UsageTarget target = targets[0];
      if (!(target instanceof PsiElementUsageTarget)) return false;
      return UsageSimilarityFeaturesProvider.EP_NAME.findFirstSafe(provider -> {
        return provider.isAvailable((PsiElementUsageTarget)target);
      }) != null;
    }

    @Override
    public @NotNull String getTabTitle() {
      return UsageViewBundle.message("similar.usages.tab.name");
    }
  }
}
