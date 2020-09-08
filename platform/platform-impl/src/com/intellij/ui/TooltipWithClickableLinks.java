// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TooltipEvent;
import com.intellij.openapi.util.NlsContexts;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * Custom tooltip implementation which supports clickable links in HTML text
 *
 * @see IdeTooltipManager#setCustomTooltip(JComponent, IdeTooltip)
 */
public class TooltipWithClickableLinks extends IdeTooltip {
  public TooltipWithClickableLinks(JComponent component, @NlsContexts.Tooltip String htmlText, HyperlinkListener hyperlinkListener) {
    super(component, new Point(), createTipComponent(htmlText, hyperlinkListener), component, htmlText);
    setHint(true);//Avoid hiding this kind of tooltips when mouse leaves component
  }

  private static JComponent createTipComponent(@NlsContexts.Tooltip String text, HyperlinkListener hyperlinkListener) {
    JEditorPane pane = IdeTooltipManager.initPane(text, new HintHint().setAwtTooltip(true), null);
    pane.addHyperlinkListener(hyperlinkListener);
    return pane;
  }

  @Override
  protected boolean canAutohideOn(TooltipEvent event) {
    return !event.isIsEventInsideBalloon();
  }

  public static class ForBrowser extends TooltipWithClickableLinks {
    public ForBrowser(JComponent component, @NlsContexts.Tooltip String htmlText) {
      super(component, htmlText, new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          BrowserUtil.browse(e.getURL());
        }
      });
    }
  }
}
