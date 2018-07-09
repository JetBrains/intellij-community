// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.IntentionsCollector;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsDifferenceSender;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

public final class IntentionUsagesCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    IntentionsCollector.State state = IntentionsCollector.getInstance().getState();
    assert state != null;
    return ContainerUtil.map2Set(state.myIntentions.entrySet(), e -> new UsageDescriptor(ensureProperKey(e.getKey()), e.getValue()));
  }

  @NotNull
  public String getGroupId() {
    return "statistics.actions.intentions";
  }
}
