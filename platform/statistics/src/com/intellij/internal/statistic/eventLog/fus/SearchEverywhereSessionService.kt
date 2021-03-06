// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import java.util.concurrent.atomic.AtomicInteger

class SearchEverywhereSessionService {
  private val counter = AtomicInteger()

  fun getSessionId() = counter.get()

  fun incAndGet() = counter.incrementAndGet()
}