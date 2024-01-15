// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.platform.ide.bootstrap.AppStarter
import com.intellij.platform.ide.bootstrap.startApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.io.BuiltInServer

@Deprecated("Please use BuiltInServerManager instead")
fun getServer(): BuiltInServer? {
  val instance = BuiltInServerManager.getInstance()
  instance.waitForStart()
  val candidate = instance.serverDisposable
  return if (candidate is BuiltInServer) candidate else null
}

@Deprecated("Use 'startApplication' with 'mainClassLoaderDeferred' parameter instead",
            ReplaceWith(
              "startApplication(args, CompletableDeferred(AppStarter::class.java.classLoader), appStarterDeferred, mainScope, busyThread)",
              "kotlinx.coroutines.CompletableDeferred"))
fun CoroutineScope.startApplication(args: List<String>, appStarterDeferred: Deferred<AppStarter>, mainScope: CoroutineScope,
                                    busyThread: Thread) {
  startApplication(args = args,
                   configImportNeededDeferred = CompletableDeferred(false),
                   targetDirectoryToImportConfig = null,
                   mainClassLoaderDeferred = CompletableDeferred(AppStarter::class.java.classLoader),
                   appStarterDeferred = appStarterDeferred,
                   mainScope = mainScope,
                   busyThread = busyThread)
}