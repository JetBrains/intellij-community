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
import com.intellij.ide.HelpTooltip;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextHelpLabel extends JBLabel {

  public ContextHelpLabel(String label, String description) {
    super(label);
    init(new HelpTooltip().setDescription(description));
  }

  private ContextHelpLabel(@NotNull HelpTooltip tooltip) {
    super(AllIcons.General.ContextHelp);
    init(tooltip);
  }

  private void init(@NotNull HelpTooltip tooltip) {
    tooltip.
      setNeverHideOnTimeout(true).
      setLocation(HelpTooltip.Alignment.HELP_BUTTON).
      installOn(this);
  }

  @NotNull
  public static ContextHelpLabel create(@NotNull String description) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description));
  }

  @NotNull
  public static ContextHelpLabel create(@NotNull String title, @NotNull String description) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title));
  }

  @NotNull
  public static ContextHelpLabel createWithLink(@Nullable String title, @NotNull String description,
                                                @NotNull String linkText, @NotNull Runnable linkAction) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title).setLink(linkText, linkAction));
  }
}
