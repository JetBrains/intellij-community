// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.EditorColorSchemesUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Deprecated // to be removed in 2018.2
public class LegacyEditorColorSchemesUsagesCollector extends UsagesCollector {

  public static final String GROUP_ID = "Color Schemes";

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return EditorColorSchemesUsagesCollector.getDescriptors();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }

}
