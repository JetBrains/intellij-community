/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Roman.Chernyatchik
 */
public class SMPoolOfTestIcons implements PoolOfTestIcons {
  // Error flag icon
  private static final Icon ERROR_ICON_MARK = IconLoader.getIcon("/nodes/errorMark.png");

  public static final Icon SKIPPED_E_ICON = addErrorMarkTo(SKIPPED_ICON);
  public static final Icon PASSED_E_ICON = addErrorMarkTo(PASSED_ICON);
  public static final Icon FAILED_E_ICON = addErrorMarkTo(FAILED_ICON);
  public static final Icon TERMINATED_E_ICON = addErrorMarkTo(TERMINATED_ICON);
  public static final Icon IGNORED_E_ICON = addErrorMarkTo(IGNORED_ICON);

  // Test Progress
  public static final Icon PAUSED_E_ICON = addErrorMarkTo(TestsProgressAnimator.PAUSED_ICON);
  public static final Icon[] FRAMES_E = new Icon[TestsProgressAnimator.FRAMES.length];
  static {
    for (int i = 0, length = FRAMES_E.length; i < length; i++){
      FRAMES_E[i] = addErrorMarkTo(TestsProgressAnimator.FRAMES[i]);
    }
  }

  @NotNull
  public static Icon addErrorMarkTo(@NotNull
  final Icon baseIcon) {
    return new LayeredIcon(baseIcon, ERROR_ICON_MARK);
  }
}
