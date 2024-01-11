// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer

@Deprecated("Please use BuiltInServerManager instead")
fun getServer(): BuiltInServer? {
  val instance = BuiltInServerManager.getInstance()
  instance.waitForStart()
  val candidate = instance.serverDisposable
  return if (candidate is BuiltInServer) candidate else null
}

