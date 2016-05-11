/*
 * Copyright 2015 Manuel Stadelmann
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
package com.intellij.usages.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;

import java.util.Arrays;
import java.util.List;

/**
 * @author Manuel Stadelmann
 */
public class RemoveUsageAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getUsages(e).length > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    process(getUsages(e), e.getData(UsageView.USAGE_VIEW_KEY));
  }

  private static void process(Usage[] usages, UsageView usageView) {
    if (usages.length == 0) return;
    Arrays.sort(usages, UsageViewImpl.USAGE_COMPARATOR);
    final Usage nextToSelect = getNextToSelect(usageView, usages[usages.length - 1]);

    for (Usage usage : usages) {
      usageView.removeUsage(usage);
    }

    if (nextToSelect != null) {
      usageView.selectUsages(new Usage[]{nextToSelect});
    }
  }

  private static Usage[] getUsages(AnActionEvent context) {
    UsageView usageView = context.getData(UsageView.USAGE_VIEW_KEY);
    if (usageView == null) return Usage.EMPTY_ARRAY;
    Usage[] usages = context.getData(UsageView.USAGES_KEY);
    return usages == null ? Usage.EMPTY_ARRAY : usages;
  }

  private static Usage getNextToSelect(UsageView usageView, Usage toDelete) {
    List<Usage> sortedUsages = usageView.getSortedUsages();
    int curIndex = sortedUsages.indexOf(toDelete);

    int selectIndex = 0;
    if (curIndex < sortedUsages.size() - 1) {
      selectIndex = curIndex + 1;
    }
    else if (curIndex > 0) {
      selectIndex = curIndex - 1;
    }
    return sortedUsages.get(selectIndex);
  }
}
