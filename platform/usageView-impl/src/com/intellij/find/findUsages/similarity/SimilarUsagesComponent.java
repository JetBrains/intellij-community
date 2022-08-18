// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageInfo2UsageAdapter;
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
  private int alreadyShown = 0;
  private final @NotNull UsageInfo myOriginalUsage;

  public SimilarUsagesComponent(@NotNull UsageInfo originalUsage, @NotNull Disposable parent) {
    myOriginalUsage = originalUsage;
    setLayout(new VerticalLayout(0));
    setBackground(UIUtil.getTextFieldBackground());
    Disposer.register(parent, this);
  }

  public void renderSimilarUsages(@NotNull Collection<SimilarUsage> similarUsagesGroupUsages) {
    if (alreadyShown < similarUsagesGroupUsages.size() - 1) {
      similarUsagesGroupUsages.stream().skip(alreadyShown).limit(SNIPPET_LIMIT).forEach(usage -> {
        final UsageInfo info = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
        if (myOriginalUsage != info) {
          renderUsage(info);
          alreadyShown++;
        }
      });
    }
  }

  private void renderUsage(@NotNull UsageInfo info) {
    PsiElement element = info.getElement();
    if (element == null) return;
    final UsageCodeSnippetComponent codeSnippet = new UsageCodeSnippetComponent(info.getElement());
    Disposer.register(this, codeSnippet);
    Color color = codeSnippet.getEditor().getBackgroundColor();
    add(getHeaderPanelForUsage(myOriginalUsage, info, color));
    add(codeSnippet);
  }

  public void renderOriginalUsage() {
    renderUsage(myOriginalUsage);
  }

  public static @NotNull JPanel getHeaderPanelForUsage(@NotNull UsageInfo originalUsage,
                                                       @NotNull UsageInfo usageInfo, @NotNull Color backGroundColor) {
    final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
    header.setBackground(backGroundColor);
    final JComponent link = new LocationLinkComponent(usageInfo).getComponent();
    header.add(link);
    if (usageInfo == originalUsage) {
      final SimpleColoredComponent component = new SimpleColoredComponent();
      component.append(UsageViewBundle.message("similar.usages.the.original.usage.label"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      header.add(component);
    }
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
    renderSimilarUsages(usagesToRender);
    BoundedRangeModelThresholdListener.install(similarUsagesScrollPane.getVerticalScrollBar(), () -> {
      renderSimilarUsages(usagesToRender);
      return Unit.INSTANCE;
    });
    return similarUsagesScrollPane;
  }
}
