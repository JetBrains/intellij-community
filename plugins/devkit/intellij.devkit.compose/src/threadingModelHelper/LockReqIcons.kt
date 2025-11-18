// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.threadingModelHelper

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object LockReqIcons {
  @JvmField
  val LockReqIcon: Icon = IconLoader.getIcon("/icons/lock-reqs.svg", LockReqIcons::class.java)
}