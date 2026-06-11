// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.util.JbCentralCliSupport
import com.intellij.agent.workbench.sessions.util.JbCentralCliSupportTestHook
import org.jetbrains.annotations.TestOnly

internal const val AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY: String = "agent.workbench.sessions.jbcentral.path"

object JbCentralQuotaCliSupport {
  fun findExecutable(): String? = JbCentralCliSupport.findExecutable()

  fun isAvailable(): Boolean = JbCentralCliSupport.isAvailable()
}

internal object JbCentralQuotaCliSupportTestHook {
  @TestOnly
  fun replacePathLookupForTest(pathLookup: (() -> String?)?): (() -> String?)? {
    return JbCentralCliSupportTestHook.replacePathLookupForTest(pathLookup)
  }
}
