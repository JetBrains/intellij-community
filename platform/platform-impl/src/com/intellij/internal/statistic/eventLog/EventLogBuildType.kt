// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

enum class EventLogBuildType(val text: String) {
  EAP("eap"), RELEASE("release"), UNKNOWN("unknown")
}