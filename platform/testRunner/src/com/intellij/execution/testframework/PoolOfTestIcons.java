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

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;

public interface PoolOfTestIcons {
  Icon SKIPPED_ICON = TestsUIUtil.loadIcon("testSkipped");
  Icon PASSED_ICON = TestsUIUtil.loadIcon("testPassed");
  Icon FAILED_ICON = TestsUIUtil.loadIcon("testFailed");
  Icon ERROR_ICON = TestsUIUtil.loadIcon("testError");
  Icon NOT_RAN = TestsUIUtil.loadIcon("testNotRan");
  Icon LOADING_ICON = TestsUIUtil.loadIcon("loadingTree");
  Icon TERMINATED_ICON = TestsUIUtil.loadIcon("testTerminated");
  Icon IGNORED_ICON = TestsUIUtil.loadIcon("testIgnored");
  Icon ERROR_ICON_MARK = IconLoader.getIcon("/nodes/errorMark.png");
  Icon TEAR_DOWN_FAILURE = new LayeredIcon(PASSED_ICON, ERROR_ICON_MARK);
}
