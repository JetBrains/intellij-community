// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import java.awt.Color

enum class AgentThreadActivity(val argb: Int) {
  READY(0xFF3FE47E.toInt()),
  PROCESSING(0xFFFF9F43.toInt()),
  REVIEWING(0xFF2FD1C4.toInt()),
  UNREAD(0xFF4DA3FF.toInt()),
  ;

  fun badgeColor(): Color = Color(argb, true)
}
