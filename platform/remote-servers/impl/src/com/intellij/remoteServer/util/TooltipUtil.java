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
public class TooltipUtil {

  public static HyperlinkLabel createTooltip(final String message) {
    final HyperlinkLabel link = new HyperlinkLabel("");
    link.setIcon(AllIcons.General.Help_small);
    link.setUseIconAsLink(true);
    link.setIconTextGap(0);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel(message);
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.INFORMATION_COLOR);
        label.setOpaque(true);
        HintManager.getInstance()
          .showHint(label, RelativePoint.getSouthEastOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });
    return link;
  }
}
