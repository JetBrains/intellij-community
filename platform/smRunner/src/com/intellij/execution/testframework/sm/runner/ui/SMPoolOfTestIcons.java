// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.icons.AllIcons;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Roman.Chernyatchik
 */
public final class SMPoolOfTestIcons implements PoolOfTestIcons {
  // Error flag icon

  public static final Icon SKIPPED_E_ICON = addErrorMarkTo(SKIPPED_ICON);
  public static final Icon PASSED_E_ICON = addErrorMarkTo(PASSED_ICON);
  public static final Icon FAILED_E_ICON = addErrorMarkTo(FAILED_ICON);
  public static final Icon TERMINATED_E_ICON = addErrorMarkTo(TERMINATED_ICON);
  public static final Icon IGNORED_E_ICON = addErrorMarkTo(IGNORED_ICON);

  // Test Progress
  public static final Icon RUNNING_ICON = new AnimatedIcon.Default();
  public static final Icon RUNNING_E_ICON = addErrorMarkTo(RUNNING_ICON);
  public static final Icon PAUSED_E_ICON = addErrorMarkTo(AllIcons.RunConfigurations.TestPaused);

  public static @NotNull Icon addErrorMarkTo(final @NotNull Icon baseIcon) {
    return LayeredIcon.layeredIcon(new Icon[]{baseIcon, ERROR_ICON_MARK});
  }
}
