// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Set;

public class SimilarUsagesComponent extends JPanel implements Disposable {

  public static final int SNIPPET_LIMIT = 10;
  private int myAlreadyProcessedUsages = 0;
  private int myAlreadyRenderedUsages = 0;
  private final @NotNull UsageInfo myOriginalUsage;
  private final @NotNull UsageView myUsageView;

  public SimilarUsagesComponent(@NotNull UsageView usageView, @NotNull UsageInfo originalUsage, @NotNull Disposable parent) {
    myOriginalUsage = originalUsage;
    myUsageView = usageView;
    setLayout(new VerticalLayout(0));
    setBackground(UIUtil.getTextFieldBackground());
    Disposer.register(parent, this);
  }

  public void renderSimilarUsages(@NotNull Collection<SimilarUsage> similarUsagesGroupUsages) {
    similarUsagesGroupUsages.stream().skip(myAlreadyProcessedUsages).limit(SNIPPET_LIMIT).forEach(usage -> {
      final UsageInfo info = usage.getUsageInfo();
      if (myOriginalUsage != info) {
        renderUsage(info);
      }
      myAlreadyProcessedUsages++;
    });
  }

  private void renderUsage(@NotNull UsageInfo info) {
    PsiElement element = info.getElement();
    PsiFile file = info.getFile();
    ProperTextRange rangeInElement = info.getRangeInElement();
    myAlreadyRenderedUsages++;
    if (element == null || file == null || rangeInElement == null) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    final UsageCodeSnippetComponent codeSnippet = new UsageCodeSnippetComponent(element, rangeInElement);
    Disposer.register(this, codeSnippet);
    JPanel headerPanelForUsage = getHeaderPanelForUsage(virtualFile, element, codeSnippet.getEditor().getBackgroundColor());
    if (myOriginalUsage==info) {
      final SimpleColoredComponent component = new SimpleColoredComponent();
      component.append(UsageViewBundle.message("similar.usages.the.original.usage.label"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      headerPanelForUsage.add(component);
    }
    add(headerPanelForUsage);
    add(codeSnippet);
  }

  public void renderOriginalUsage() {
    renderUsage(myOriginalUsage);
  }

  public @NotNull JPanel getHeaderPanelForUsage(@NotNull VirtualFile virtualFile,
                                                @NotNull PsiElement element,
                                                @NotNull Color backGroundColor) {
    final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
    header.setBackground(backGroundColor);
    final JComponent link = new LocationLinkComponent(this, myUsageView, element, virtualFile).getComponent();
    header.add(link);
    final Color color = new JBColor(Gray.xCD, Gray.x51);
    header.setBorder(JBUI.Borders.customLineTop(color));
    return header;
  }

  @Override
  public void dispose() {
  }

  public @NotNull JScrollPane createLazyLoadingScrollPane(@NotNull Set<SimilarUsage> usagesToRender) {
    JScrollPane similarUsagesScrollPane = ScrollPaneFactory.createScrollPane(this, true);
    renderOriginalUsage();
    BoundedRangeModelThresholdListener.install(similarUsagesScrollPane.getVerticalScrollBar(), () -> {
      if (myAlreadyProcessedUsages < usagesToRender.size()) {
        renderSimilarUsages(usagesToRender);
        SimilarUsagesCollector.logMoreUsagesLoaded(myOriginalUsage.getProject(), myUsageView, myAlreadyRenderedUsages);
      }
      return Unit.INSTANCE;
    });
    return similarUsagesScrollPane;
  }
}
