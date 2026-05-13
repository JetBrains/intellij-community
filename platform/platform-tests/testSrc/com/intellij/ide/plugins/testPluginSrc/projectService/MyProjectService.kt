// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.projectService

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.platform.testFramework.plugins.PluginTestHandle

internal class MyStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val service = project.getService(MyProjectService::class.java)
    if (service != null) {
      service.executed = true
    }
  }
}

internal class MyProjectService : PluginTestHandle<Unit, Boolean> {
  companion object {
    val LOG = logger<MyProjectService>()
  }

  init {
    LOG.info("MyProjectService initialized")
  }

  var executed = false

  override fun test(arg: Unit): Boolean = executed
}
