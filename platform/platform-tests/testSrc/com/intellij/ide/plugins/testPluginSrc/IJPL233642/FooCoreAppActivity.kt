// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.IJPL233642

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.plugins.PluginTestHandle
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

@Service
internal class FooCore : PluginTestHandle {
  val invoked = CompletableDeferred<Unit>()
  override fun test() {
    runBlocking {
      withTimeout(1.minutes) {
        invoked.join()
      }
    }
  }
}

@Suppress("UnresolvedPluginConfigReference")
internal class FooCoreAppActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    assert(Registry.`is`("foo.module.registry.key"))
    application.getService(FooCore::class.java).invoked.complete(Unit)
  }
}


