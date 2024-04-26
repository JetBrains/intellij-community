// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public interface FeatureUsageCollectorExtension {
  ExtensionPointName<FeatureUsageCollectorExtension> EP_NAME = new ExtensionPointName<>("com.intellij.statistics.collectorExtension");

  @NonNls
  String getGroupId();
  String getEventId();

  List<EventField> getExtensionFields();
}
