// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.monorepo.MonorepoProjectStructure
import com.intellij.platform.testFramework.monorepo.api.PlatformApi.isPlatformModule
import com.intellij.platform.testFramework.monorepo.hasProductionSources
import com.intellij.tools.apiDump.dumpApi
import com.intellij.util.io.delete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Generates and overwrites [MODULE_API_DUMP_FILE_NAME] or [MODULE_API_DUMP_UNREVIEWED_FILE_NAME]
 * in modules which match [isPlatformModule].
 */
fun main(): Unit = runBlocking {
  generateApiDumps(this, MonorepoProjectStructure.communityProject.modules)
}

suspend fun generateApiDumps(coroutineScope: CoroutineScope, wantedModules: List<JpsModule>) {
  val modules = wantedModules
    .filter {
      it.isPlatformModule()
      || it.firstContentRoot().let { it != null && (it / MODULE_API_DUMP_FILE_NAME).exists() }
    }
    .filter(JpsModule::hasProductionSources)
    .toList()
  val moduleApi = ModuleApi(coroutineScope + Dispatchers.Default)
  for (module in modules) {
    moduleApi.discoverModule(module)
  }
  val exposedThirdPartyApiFilter: FileApiClassFilter = globalExposedThirdPartyClasses(modules)
  var reviewedModules = 0
  var privateApiExposures = 0
  var privateApiExposuresInUnreviewedModules = 0
  for (module in modules) {
    val contentRootPath = module.firstContentRoot() ?: continue
    var reviewed = true
    var stableApiDumpFilePath = contentRootPath / MODULE_API_DUMP_FILE_NAME
    if (!stableApiDumpFilePath.exists()) {
      stableApiDumpFilePath = contentRootPath / MODULE_API_DUMP_UNREVIEWED_FILE_NAME
      reviewed = false
    }
    var experimentalApiDumpFilePath = contentRootPath / MODULE_API_DUMP_EXPERIMENTAL_FILE_NAME
    println("- [${if (reviewed) "x" else " "}] ${module.name}")
    val api = moduleApi.moduleApi(module)
    stableApiDumpFilePath.writeText(dumpApi(api.stableApi))
    if (api.experimentalApi.isEmpty()) {
      experimentalApiDumpFilePath.delete()
    }
    else {
      experimentalApiDumpFilePath.writeText(dumpApi(api.experimentalApi))
    }
    val (exposedThirdPartyClasses, exposedPrivateClasses) = exposedApi(api, exposedThirdPartyApiFilter)
    listExposures(exposedThirdPartyClasses, contentRootPath / exposedThirdPartyApiFileName)
    listExposures(exposedPrivateClasses, contentRootPath / exposedPrivateApiFileName)
    if (reviewed) {
      reviewedModules++
      privateApiExposures += exposedPrivateClasses.values.sumOf { it.size }
    }
    else {
      privateApiExposuresInUnreviewedModules += exposedPrivateClasses.values.sumOf { it.size }
    }
  }
  println("Reviewed modules: $reviewedModules of ${modules.size}")
  println("Exposed private API in reviewed/unreviewed modules: $privateApiExposures/$privateApiExposuresInUnreviewedModules")
}

private fun listExposures(exposures: Map<String, *>, out: Path) {
  if (exposures.isEmpty()) {
    out.delete()
  }
  else {
    out.writeText(
      exposures.keys.sorted().joinToString(separator = "\n", postfix = "\n")
    )
  }
}
