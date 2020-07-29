// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

/**
 * @author michael.golubev
 */
public final class TooltipUtil {

  public static HyperlinkLabel createTooltip(final String message) {
    final HyperlinkLabel link = new HyperlinkLabel("");
    link.setIcon(AllIcons.General.ContextHelp);
    link.setUseIconAsLink(true);
    link.setIconTextGap(0);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel(message);
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance()
          .showHint(label, RelativePoint.getSouthEastOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });
    return link;
  }
}
