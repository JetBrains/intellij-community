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
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

public class LaFUsagesCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    String key = SystemInfo.OS_NAME + " - ";
    if (!StringUtil.isEmptyOrSpaces(SystemInfo.SUN_DESKTOP)) {
      key += SystemInfo.SUN_DESKTOP + " - ";
    }
    return laf != null ? Collections.singleton(new UsageDescriptor(key + laf.getName(), 1))
                       : Collections.emptySet();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Look and Feel");
  }
}
