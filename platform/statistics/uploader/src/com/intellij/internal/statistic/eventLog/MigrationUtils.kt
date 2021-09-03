// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.jetbrains.fus.reporting.model.lion3.LogEventAction

fun LogEventAction.addData(key: String, value: Any) {
  this.data[key] = value //todo (ivanova) escape?
}