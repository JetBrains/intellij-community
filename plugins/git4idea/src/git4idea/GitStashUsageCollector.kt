// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class GitStashUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("stash.interactions", 1)

    private val STASH_PUSH = GROUP.registerEvent("stash_push",
                                            EventFields.DurationMs)

    private val STASH_POP = GROUP.registerEvent("stash_pop",
                                              EventFields.DurationMs)

    @JvmStatic
    fun logStashPush(startMs: Long) {
      STASH_PUSH.log(System.currentTimeMillis() - startMs)
    }

    @JvmStatic
    fun logStashPop(startMs: Long) {
      STASH_POP.log(System.currentTimeMillis() - startMs)
    }
  }
}