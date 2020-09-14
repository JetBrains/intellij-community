// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum StatisticsCollectorType {
  COUNTER("com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector", "statistics.counterUsagesCollector",
          "implementationClass"),
  PROJECT("com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector", "statistics.projectUsagesCollector",
          "implementation"),
  APPLICATION("com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector", "statistics.applicationUsagesCollector",
              "implementation");

  private final String myClassName;
  private final String myExtensionPoint;
  private final String myImplementationAttribute;

  StatisticsCollectorType(String className, String extensionPoint, String implementationAttribute) {
    this.myClassName = className;
    this.myExtensionPoint = extensionPoint;
    this.myImplementationAttribute = implementationAttribute;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getExtensionPoint() {
    return myExtensionPoint;
  }

  public String getImplementationAttribute() {
    return myImplementationAttribute;
  }

  public static @Nullable StatisticsCollectorType findByExtensionPoint(@NotNull String extensionPoint) {
    for (StatisticsCollectorType collectorType : values()) {
      if (collectorType.getExtensionPoint().equals(extensionPoint)) {
        return collectorType;
      }
    }
    return null;
  }
}
