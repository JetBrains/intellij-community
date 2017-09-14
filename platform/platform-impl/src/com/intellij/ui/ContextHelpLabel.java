/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TooltipEvent;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ContextHelpLabel extends JBLabel {

  public ContextHelpLabel(@NotNull String helpText) {
    super(AllIcons.General.ContextHelp);
    IdeTooltipManager.getInstance().setCustomTooltip(this, new ContextHelpTooltip(this, helpText));
  }

  private static class ContextHelpTooltip extends TooltipWithClickableLinks.ForBrowser {
    public ContextHelpTooltip(@NotNull JComponent component, @NotNull String text) {
      super(component, text);

      JBInsets insets = JBUI.insets(11, 10, 11, 17);
      setBorderInsets(insets);
      setPreferredPosition(Balloon.Position.below);
      setCalloutShift(insets.top);
      setBorderColor(new JBColor(Gray._161, new Color(91, 92, 94)));
      setTextBackground(new JBColor(Gray._247, new Color(70, 72, 74)));
      setTextForeground(new JBColor(Gray._33, Gray._191));
    }

    @Override
    protected boolean canAutohideOn(TooltipEvent event) {
      return event.getInputEvent() != null && super.canAutohideOn(event);
    }

    @Override
    public int getShowDelay() {
      return 0;
    }

    @Override
    public boolean canBeDismissedOnTimeout() {
      return true;
    }
  }
}
