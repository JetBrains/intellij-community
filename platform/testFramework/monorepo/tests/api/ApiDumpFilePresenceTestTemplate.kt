// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.api.PlatformApi.isPlatformModule
import com.intellij.platform.testFramework.monorepo.hasProductionSources
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.fail
import kotlin.io.path.exists

fun checkModulesDefineApiDump(modules: List<JpsModule>) {
  val modulesWithoutApiDump = modules
    .filter { module ->
      module.isPlatformModule()
      && module.hasProductionSources() // skip modules without sources, for example, modules with tests
      && module.firstContentRoot().let { contentRoot ->
        contentRoot != null // skip modules without content roots
        && !contentRoot.stableApiDumpPath().exists()
      }
    }
  if (modulesWithoutApiDump.isNotEmpty()) {
    fail {
      "Platform modules should define 'api-dump.txt' in the first content root. " +
      "Modules without API dump:\n" +
      modulesWithoutApiDump.joinToString("\n") { it.name }
    }
  }
}