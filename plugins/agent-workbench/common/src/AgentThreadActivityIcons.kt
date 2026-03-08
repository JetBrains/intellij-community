// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import com.intellij.ui.IconManager
import javax.swing.Icon

fun withAgentThreadActivityBadge(baseIcon: Icon, activity: AgentThreadActivity): Icon {
  return IconManager.getInstance().withIconBadge(baseIcon, activity.statusColor())
}
