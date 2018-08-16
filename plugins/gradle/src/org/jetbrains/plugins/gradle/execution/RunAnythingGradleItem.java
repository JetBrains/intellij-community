// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import groovyjarjarcommonscli.Option;
import groovyjarjarcommonscli.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladislav.Soroka
 */
public class RunAnythingGradleItem extends RunAnythingItemBase {

  public RunAnythingGradleItem(@NotNull String command, @Nullable Icon icon) {
    super(command, icon);
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    String command = getCommand();

    int spaceIndex = StringUtil.lastIndexOf(command, ' ', 0, command.length());
    String toComplete = spaceIndex < 0 ? "" : command.substring(spaceIndex + 1);
    String description = null;
    if (toComplete.startsWith("-")) {
      boolean isLongOpt = toComplete.startsWith("--");
      Options options = GradleCommandLineOptionsProvider.getSupportedOptions();
      Option option = options.getOption(toComplete.substring(isLongOpt ? 2 : 1));
      if (option != null) {
        description = option.getDescription();
      }
    }
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.append(command);
    component.appendTextPadding(20);
    setupIcon(component, myIcon);

    if (description != null) {
      component.append(" " + StringUtil.shortenTextWithEllipsis(description, 200, 0), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
    }
    return component;
  }
}