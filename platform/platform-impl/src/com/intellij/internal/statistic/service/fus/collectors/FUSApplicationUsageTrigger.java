// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

@State(name = "FUSApplicationUsageTrigger",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED))
final public class FUSApplicationUsageTrigger extends AbstractUsageTrigger<ApplicationUsageTriggerCollector>
  implements PersistentStateComponent<AbstractUsageTrigger.State> {

  @Override
  protected FeatureUsagesCollector findCollector(@NotNull Class<? extends ApplicationUsageTriggerCollector> fusClass) {
    for (ApplicationUsagesCollector collector : ApplicationUsagesCollector.getExtensions(this)) {
      if (fusClass.equals(collector.getClass())) {
        return collector;
      }
    }
    return null;
  }

  @Override
  protected FUSession getFUSession() {
    return FUSession.APPLICATION_SESSION;
  }

  public static FUSApplicationUsageTrigger getInstance() {
    return ServiceManager.getService(FUSApplicationUsageTrigger.class);
  }
}
