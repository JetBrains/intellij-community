/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TooltipEvent;

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
  public TooltipWithClickableLinks(JComponent component, String htmlText, HyperlinkListener hyperlinkListener) {
    super(component, new Point(), createTipComponent(htmlText, hyperlinkListener), component, htmlText);
  }

  private static JComponent createTipComponent(String text, HyperlinkListener hyperlinkListener) {
    JEditorPane pane = IdeTooltipManager.initPane(text, new HintHint().setAwtTooltip(true), null);
    pane.addHyperlinkListener(hyperlinkListener);
    return pane;
  }

  @Override
  protected boolean canAutohideOn(TooltipEvent event) {
    return !event.isIsEventInsideBalloon();
  }

  public static class ForBrowser extends TooltipWithClickableLinks {
    public ForBrowser(JComponent component, String htmlText) {
      super(component, htmlText, new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          BrowserUtil.browse(e.getURL());
        }
      });
    }
  }
}
