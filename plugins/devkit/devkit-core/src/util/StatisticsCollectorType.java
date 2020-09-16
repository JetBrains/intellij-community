// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

public enum StatisticsCollectorType {
  COUNTER("com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector", "com.intellij.statistics.counterUsagesCollector"),
  PROJECT("com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector", "com.intellij.statistics.projectUsagesCollector"),
  APPLICATION("com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector", "com.intellij.statistics.applicationUsagesCollector");

  private final String myClassName;
  private final String myExtensionPoint;

  StatisticsCollectorType(String className, String extensionPoint) {
    this.myClassName = className;
    this.myExtensionPoint = extensionPoint;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getExtensionPoint() {
    return myExtensionPoint;
  }
}
