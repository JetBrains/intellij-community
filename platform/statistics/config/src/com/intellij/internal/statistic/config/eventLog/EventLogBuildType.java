// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config.eventLog;

public enum EventLogBuildType {
  EAP("eap"), RELEASE("release"), UNKNOWN("unknown"), ALL("all");

  public final String text;

  EventLogBuildType(String text) {
    this.text = text;
  }
}
