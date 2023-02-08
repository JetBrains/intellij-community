// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.ActionLink;
import com.intellij.usages.UsageView;
import com.intellij.util.IconUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;


import static com.intellij.usages.similarity.statistics.SimilarUsagesCollector.logNavigateToUsageClicked;

public class LocationLinkComponent {

  @NotNull
  private final ActionLink myLabel;

  public LocationLinkComponent(@NotNull JComponent component, @NotNull UsageView usageView, @NotNull PsiElement element, @NotNull VirtualFile file) {
    myLabel = new ActionLink(file.getName(), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        logNavigateToUsageClicked(element.getProject(), component.getClass(), usageView);
        PsiNavigateUtil.navigate(element);
      }
    });

    myLabel.setBackground(UIUtil.TRANSPARENT_COLOR);
    myLabel.setOpaque(false);
    myLabel.setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, element.getProject()));
  }

  public @NotNull JComponent getComponent() {
    return myLabel;
  }
}
