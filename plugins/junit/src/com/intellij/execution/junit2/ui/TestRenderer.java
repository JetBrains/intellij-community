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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.junit2.ui.model.SpecialNode;
import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.icons.AllIcons;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Map;

class TestRenderer {
  private static final Map<Integer,Icon> ourIcons = new HashMap<Integer, Icon>();

  public static Icon getIconFor(final TestProxy testProxy, final boolean isPaused) {
    final int magnitude = testProxy.getState().getMagnitude();
    if (magnitude == PoolOfTestStates.RUNNING_INDEX)
      return isPaused ? AllIcons.RunConfigurations.TestPaused : TestsProgressAnimator.getCurrentFrame();
    else
      return ourIcons.get(new Integer(magnitude));
  }

  private static SimpleTextAttributes getSpecialAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, TestsUIUtil.PASSED_COLOR);
  }

  static {
    mapIcon(PoolOfTestStates.SKIPPED_INDEX, PoolOfTestIcons.SKIPPED_ICON);
    mapIcon(PoolOfTestStates.NOT_RUN_INDEX, PoolOfTestIcons.NOT_RAN);
    mapIcon(PoolOfTestStates.PASSED_INDEX, PoolOfTestIcons.PASSED_ICON);
    mapIcon(PoolOfTestStates.TERMINATED_INDEX, PoolOfTestIcons.TERMINATED_ICON);
    mapIcon(PoolOfTestStates.FAILED_INDEX, PoolOfTestIcons.FAILED_ICON);
    mapIcon(PoolOfTestStates.COMPARISON_FAILURE, PoolOfTestIcons.FAILED_ICON);
    mapIcon(PoolOfTestStates.ERROR_INDEX, PoolOfTestIcons.ERROR_ICON);
    mapIcon(PoolOfTestStates.IGNORED_INDEX, PoolOfTestIcons.IGNORED_ICON);
  }

  private static void mapIcon(final int index, final Icon icon) {
    ourIcons.put(new Integer(index), icon);
  }

  public static void renderTest(final TestProxy test, final SimpleColoredComponent renderer) {
    final TestInfo info = test.getInfo();
    if (test instanceof SpecialNode) {
      renderer.append(info.getName(), getSpecialAttributes());
    } else {
      renderer.append(info.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append(Formatters.sensibleCommentFor(test), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
