// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public interface FeatureUsageCollectorExtension {
  ExtensionPointName<FeatureUsageCollectorExtension> EP_NAME = ExtensionPointName.create("com.intellij.statistics.collectorExtension");

  @NonNls
  String getGroupId();
  String getEventId();

  List<EventField> getExtensionFields();
}
