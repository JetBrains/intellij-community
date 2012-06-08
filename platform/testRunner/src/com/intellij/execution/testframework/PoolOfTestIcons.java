/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;

public interface PoolOfTestIcons {
  Icon SKIPPED_ICON = AllIcons.RunConfigurations.TestSkipped;
  Icon PASSED_ICON = AllIcons.RunConfigurations.TestPassed;
  Icon FAILED_ICON = AllIcons.RunConfigurations.TestFailed;
  Icon ERROR_ICON = AllIcons.RunConfigurations.TestError;
  Icon NOT_RAN = AllIcons.RunConfigurations.TestNotRan;
  Icon LOADING_ICON = AllIcons.RunConfigurations.LoadingTree;
  Icon TERMINATED_ICON = AllIcons.RunConfigurations.TestTerminated;
  Icon IGNORED_ICON = AllIcons.RunConfigurations.TestIgnored;
  Icon ERROR_ICON_MARK = AllIcons.Nodes.ErrorMark;
  Icon TEAR_DOWN_FAILURE = new LayeredIcon(PASSED_ICON, ERROR_ICON_MARK);
}
