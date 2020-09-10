// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.singleRowLayout;

import com.intellij.application.options.editor.EditorTabPlacementKt;
import com.intellij.ide.IdeBundle;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CompressibleSingleRowLayout extends SingleRowLayout {

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    calculateRequiredLength(data);
  }

  @Override
  protected void layoutLabels(SingleRowPassInfo data) {
    if (myCallback.getTabsPosition() != JBTabsPosition.top
        && myCallback.getTabsPosition() != JBTabsPosition.bottom) {
      super.layoutLabels(data);
      return;
    }

    int maxGridSize = 0;
    int spentLength = 0;
    int lengthEstimation = 0;

    int[] lengths = new int[data.toLayout.size()];

    List<TabInfo> layout = data.toLayout;
    for (int i = 0; i < layout.size(); i++) {
      final TabLabel label = myCallback.getTabLabel(layout.get(i));
      if (maxGridSize == 0) {
        Font font = label.getLabelComponent().getFont();
        maxGridSize = GraphicsUtil.stringWidth("m", font == null ? JBFont.label() : font) * myCallback.tabMSize();
      }
      int lengthIncrement = label.getPreferredSize().width;
      lengths[i] = lengthIncrement;
      lengthEstimation += lengthIncrement;
    }

    final int extraWidth = data.toFitLength - lengthEstimation;

    Arrays.sort(lengths);
    double acc = 0;
    int actualGridSize = 0;
    for (int i = 0; i < lengths.length; i++) {
      int length = lengths[i];
      acc += length;
      actualGridSize = (int)Math.min(maxGridSize, (acc + extraWidth) / (i + 1));
      if (i < lengths.length - 1 && actualGridSize < lengths[i + 1]) break;
    }


    for (Iterator<TabInfo> iterator = data.toLayout.iterator(); iterator.hasNext(); ) {
      final TabLabel label = myCallback.getTabLabel(iterator.next());

      int length;
      int lengthIncrement = label.getPreferredSize().width;
      if (!iterator.hasNext()) {
        length = Math.min(data.toFitLength - spentLength, Math.max(actualGridSize, lengthIncrement));
      }
      else if (extraWidth <= 0) {//need compress
        length = (int)(lengthIncrement * (float)data.toFitLength / lengthEstimation);
      }
      else {
        length = Math.max(lengthIncrement, actualGridSize);
      }
      spentLength += length - myCallback.getBorderThickness();
      applyTabLayout(data, label, length);
      data.position = (int)label.getBounds().getMaxX() - myCallback.getBorderThickness();
    }

    for (TabInfo eachInfo : data.toDrop) {
      resetLayout(myCallback.getTabLabel(eachInfo));
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    boolean result = super.applyTabLayout(data, label, length);
    label.setAlignmentToCenter(false);
    return result;
  }

  public static class CompressibleSingleRowTabsLayoutInfo extends TabsLayoutInfo {

    private static final String ID = "CompressibleSingleRowLayoutInfo";

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    @Nls
    public String getName() {
      return IdeBundle.message("tabs.layout.compressible.name");
    }

    @NotNull
    @Override
    protected TabsLayout createTabsLayoutInstance() {
      return new CompressibleSingleRowLayout();
    }

    @Nullable
    @Override
    public Integer[] getAvailableTabsPositions() {
      return EditorTabPlacementKt.getTAB_PLACEMENTS();
    }
  }
}
