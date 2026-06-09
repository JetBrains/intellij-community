// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

enum class AgentThreadActivity {
  READY,
  PROCESSING,
  REVIEWING,
  NEEDS_INPUT,
  UNREAD,
}

data class AgentThreadActivityReport(
  @JvmField val rowActivity: AgentThreadActivity,
  @JvmField val chromeActivity: AgentThreadActivity? = rowActivity,
) {
  companion object {
    @JvmField
    val READY: AgentThreadActivityReport = AgentThreadActivityReport(AgentThreadActivity.READY)
  }
}

enum class AgentThreadActivityBucket {
  ATTENTION,
  RUNNING,
  DONE,
}

val AgentThreadActivity.isWorking: Boolean
  get() = this == AgentThreadActivity.PROCESSING || this == AgentThreadActivity.REVIEWING

fun AgentThreadActivityReport.chromeBucket(): AgentThreadActivityBucket? {
  return when (chromeActivity) {
    AgentThreadActivity.NEEDS_INPUT,
    AgentThreadActivity.REVIEWING,
      -> AgentThreadActivityBucket.ATTENTION

    AgentThreadActivity.PROCESSING -> AgentThreadActivityBucket.RUNNING
    AgentThreadActivity.UNREAD -> AgentThreadActivityBucket.DONE
    AgentThreadActivity.READY,
    null,
      -> null
  }
}
