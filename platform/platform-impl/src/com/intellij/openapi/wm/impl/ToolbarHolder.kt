// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionGroup

internal interface ToolbarHolder {
  fun initToolbar(toolbarActionGroups: List<Pair<ActionGroup, String>>)

  fun updateToolbar()

  fun removeToolbar()
}