// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.tools.apiDump.API
import com.intellij.tools.apiDump.ApiClass
import com.intellij.tools.apiDump.ClassMembers
import com.intellij.tools.apiDump.ClassName
import com.intellij.tools.apiDump.dumpApiAndGroupByClasses
import com.intellij.util.diff.Diff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.setAttribute
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

fun performApiCheckTest(cs: CoroutineScope, wantedModules: List<JpsModule>): List<DynamicTest> = buildList {
  val modules = wantedModules.prepareModuleList()

  val exposedThirdPartyApiFilter: FileApiClassFilter = globalExposedThirdPartyClasses(modules)
  val projectApi = ProjectApi(cs)
  for (module in modules) {
    val contentRootPath = module.firstContentRoot() ?: continue
    val stableApiDumpPath = contentRootPath.stableApiDumpPath()
    val unreviewedApiDumpPath = contentRootPath.unreviewedApiDumpPath()
    if (!stableApiDumpPath.exists() && !unreviewedApiDumpPath.exists()) {
      continue
    }
    val experimentalApiDumpPath = contentRootPath.experimentalApiDumpPath() // may not exist
    projectApi.discoverModule(module)
    this += DynamicTest.dynamicTest(module.getTestName()) {
      val moduleName = module.name
      val api = runBlocking {
        projectApi.moduleApi(module)
      }
      val checks = ArrayList<() -> Unit>(7)
      checks.addAll(checkModuleDump(moduleName, stableApiDumpPath, unreviewedApiDumpPath, api.stableApi))
      checks.addAll(checkModuleDump(moduleName, experimentalApiDumpPath, null, api.experimentalApi))

      val privateApiFilter = (contentRootPath / exposedPrivateApiFileName).apiClassFilter()
      val thirdPartyApiFilter = (contentRootPath / exposedThirdPartyApiFileName).apiClassFilter()

      val apiFilter: ApiClassFilter = exposedThirdPartyApiFilter
        .andThen(privateApiFilter ?: ApiClassFilter.Empty)
        .andThen(thirdPartyApiFilter ?: ApiClassFilter.Empty)
      checks.add {
        checkExposedApi(moduleName, api, apiFilter)
      }

      fun checkFilter(filter: FileApiClassFilter?) {
        if (filter == null) {
          return
        }
        checks.add {
          filter.checkAllEntriesAreUsed()
        }
        checks.add {
          filter.checkEntriesSortedAndUnique()
        }
      }
      checkFilter(privateApiFilter)
      checkFilter(thirdPartyApiFilter)

      assertAll(checks)
    }
  }
  this += DynamicTest.dynamicTest(exposedThirdPartyApiFileName) {
    assertAll(
      {
        exposedThirdPartyApiFilter.checkAllEntriesAreUsed()
      },
      {
        exposedThirdPartyApiFilter.checkEntriesSortedAndUnique()
      },
    )
  }
}

private fun JpsModule.getTestName(): String =
  // without this, TC treats the part after the last dot as a test name
  name.replace(".", "-")

internal fun globalExposedThirdPartyClasses(modules: List<JpsModule>): FileApiClassFilter =
  modules
    .first { it.name == "intellij.platform.testFramework.monorepo" }
    .contentRootsList.urls
    .firstNotNullOf { Path.of(JpsPathUtil.urlToPath(it), exposedThirdPartyApiFileName) }
    .apiClassFilter()!!

const val MODULE_API_DUMP_FILE_NAME = "api-dump.txt"
const val MODULE_API_DUMP_EXPERIMENTAL_FILE_NAME = "api-dump-experimental.txt"
const val MODULE_API_DUMP_UNREVIEWED_FILE_NAME = "api-dump-unreviewed.txt"

internal fun JpsModule.firstContentRoot(): Path? {
  val contentRoot = contentRootsList.urls.firstOrNull()
                    ?: return null
  return Path.of(JpsPathUtil.urlToPath(contentRoot))
}

internal fun Path.stableApiDumpPath(): Path = this / MODULE_API_DUMP_FILE_NAME

internal fun Path.unreviewedApiDumpPath(): Path = this / MODULE_API_DUMP_UNREVIEWED_FILE_NAME

internal fun Path.experimentalApiDumpPath(): Path {
  return this / MODULE_API_DUMP_EXPERIMENTAL_FILE_NAME
}

private data class ActualDump(
  val path: Path,
  val rawContent: String,
  val pathExists: Boolean,
  val categorizedDump: Map<ClassName, ClassMembers>,
  val simplifiedBuilder: StringBuilder = StringBuilder(),
) {
  companion object {
    fun build(path: Path): ActualDump {
      val exists = path.exists()
      val content = if (exists) path.readText().replace("\r\n", "\n") else ""
      val lines = Diff.splitLines(content)
      return ActualDump(path, content, exists, lines.groupByClasses())
    }
  }
}

private fun checkModuleDump(
  moduleName: String,
  primaryActualDumpPath: Path,
  secondaryActualDumpPath: Path?,
  classSignatures: List<ApiClass>,
): List<() -> Unit> {
  val primaryActualDump = ActualDump.build(primaryActualDumpPath)
  val secondaryActualDump = secondaryActualDumpPath?.let { ActualDump.build(it) }
  val actualCategorizedDump = dumpApiAndGroupByClasses(classSignatures)
  // partition of the actual dump that corresponds to the primary dump
  val primaryExpectedBuilder = StringBuilder()
  // partition of the actual dump that corresponds to the secondary dump
  val secondaryExpectedBuilder = StringBuilder()
  val mutablePrimaryDump = primaryActualDump.categorizedDump.toMutableMap()
  val mutableSecondaryDump = secondaryActualDump?.categorizedDump?.toMutableMap()
  for ((className, expectedMembers) in actualCategorizedDump) {
    val primaryMembers = mutablePrimaryDump.remove(className)
    val secondaryMembers = mutableSecondaryDump?.remove(className)

    if (primaryMembers == null && secondaryMembers == null) {
      // new classes go to the primary dump by default
      primaryExpectedBuilder.appendLine(className)
      primaryActualDump.simplifiedBuilder.appendLine("+ $className")
      expectedMembers.forEach {
        primaryExpectedBuilder.appendLine(it)
        primaryActualDump.simplifiedBuilder.appendLine("+ $it")
      }
      continue
    }

    // members that are physically written in `api-dump*.txt`
    val serializedPrimaryMembers = primaryMembers?.toMutableSet()
    val serializedSecondaryMembers = secondaryMembers?.toMutableSet()

    // containers for actual API members
    val actualPrimaryMembers = mutableListOf<String>()
    val actualSecondaryMembers = mutableListOf<String>()

    var headerAdded = false

    for (expectedMember in expectedMembers) {
      if (serializedPrimaryMembers?.contains(expectedMember) == true) {
        // member is mentioned in the primary dump; skipping it
        serializedPrimaryMembers.remove(expectedMember)
        actualPrimaryMembers.add(expectedMember)
      }
      else if (serializedSecondaryMembers?.contains(expectedMember) == true) {
        // member is mentioned in the secondary dump; skipping it
        serializedSecondaryMembers.remove(expectedMember)
        actualSecondaryMembers.add(expectedMember)
      }
      else {
        // member is not mentioned anywhere; adding it to the primary dump by default
        if (!headerAdded) {
          if (primaryMembers == null) {
            // the class was not previously mentioned in the primary dump, let's add class name
            primaryActualDump.simplifiedBuilder.appendLine("+ $className")
          }
          headerAdded = true
        }
        primaryActualDump.simplifiedBuilder.appendLine("+ $expectedMember")
        actualPrimaryMembers.add(expectedMember)
      }
    }

    // building partition of expected API dump by primary and secondary dumps
    appendMembersToExpectedDump(primaryExpectedBuilder, actualPrimaryMembers, className, primaryMembers != null)
    appendMembersToExpectedDump(secondaryExpectedBuilder, actualSecondaryMembers, className, secondaryMembers != null)

    // suggesting to remove leftover members from the primary
    removeLeftoverMethods(serializedPrimaryMembers, primaryActualDump.simplifiedBuilder)
    if (serializedSecondaryMembers?.isNotEmpty() == true && serializedSecondaryMembers.size == secondaryMembers.size) {
      // we moved all members from secondary dump; let's remove the class name altogether
      secondaryActualDump.simplifiedBuilder.appendLine("- $className")
    }
    removeLeftoverMethods(serializedSecondaryMembers, secondaryActualDump?.simplifiedBuilder)
  }

  cleanupClassesThatDoNotExist(mutablePrimaryDump, primaryActualDump)
  if (mutableSecondaryDump != null) {
    cleanupClassesThatDoNotExist(mutableSecondaryDump, secondaryActualDump)
  }

  fun runCheck(actualDump: ActualDump, expectedDump: String) {
    if (actualDump.rawContent == expectedDump) {
      return
    }
    val expectedDumpPath: Path? = try {
      Files.createTempFile("expected_${actualDump.path.fileName}".removeSuffix(".txt"), ".txt")
        .also {
          it.writeText(expectedDump)
          if ("dos" in it.fileSystem.supportedFileAttributeViews()) {
            it.setAttribute("dos:readonly", true)
          }
          else {
            it.setPosixFilePermissions(setOf(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.OTHERS_READ,
            ))
          }
        }
    }
    catch (e: Throwable) {
      e.printStackTrace()
      null
    }

    throw FileComparisonFailedError(
      message =
        "'$moduleName' ${actualDump.path.fileName} does not match the actual API. " +
        "If the API change was intentional, please ${if (actualDump.pathExists) "update" else "create"} '${actualDump.path}', " +
        "otherwise revert the change. " + "Simplified diff:\n" + actualDump.simplifiedBuilder.toString(),
      expected = expectedDump,
      expectedFilePath = expectedDumpPath?.toString(),
      actual = actualDump.rawContent,
      actualFilePath = if (actualDump.pathExists) actualDump.path.toString() else null)
  }

  return listOf(
    {
      runCheck(primaryActualDump, primaryExpectedBuilder.toString())
    }, {
      if (secondaryActualDump != null) {
        runCheck(secondaryActualDump, secondaryExpectedBuilder.toString())
      }
    })

}

private fun appendMembersToExpectedDump(
  expectedBuilder: StringBuilder,
  actualMembers: List<String>,
  className: String,
  existsInDump: Boolean,
) {
  if (actualMembers.isNotEmpty() || existsInDump) {
    expectedBuilder.appendLine(className)
    actualMembers.forEach { expectedBuilder.appendLine(it) }
  }
}

private fun removeLeftoverMethods(serializedMembers: Set<String>?, simplifiedBuilder: StringBuilder?) {
  if (serializedMembers?.isNotEmpty() == true) {
    checkNotNull(simplifiedBuilder) { "simplified dump is expected to be not null" }
    serializedMembers.forEach {
      simplifiedBuilder.appendLine("- $it")
    }
  }
}

private fun cleanupClassesThatDoNotExist(classes: Map<ClassName, ClassMembers>, expectedDump: ActualDump) {
  for ((redundantName, redundantMembers) in classes) {
    expectedDump.simplifiedBuilder.appendLine("- $redundantName")
    redundantMembers.forEach { expectedDump.simplifiedBuilder.appendLine("- $it") }
  }
}


private fun Array<String>.groupByClasses(): Map<ClassName, ClassMembers> {
  return fold(mutableMapOf<String, MutableList<String>>() to "") { (acc, className), s ->
    if (s.isBlank()) {
      return@fold acc to className
    }
    if (!s.startsWith("-")) {
      acc[s] = mutableListOf()
      acc to s
    }
    else {
      acc[className]!!.add(s)
      acc to className
    }
  }.first
}

private fun checkExposedApi(moduleName: String, api: API, apiFilter: ApiClassFilter) {
  val (exposedThirdPartyApi, exposedPrivateApi) = exposedApi(api, apiFilter)
  assertAll(
    { assertExposedApi(exposedThirdPartyApi, "'${moduleName}' Third party classes are exposed through API") },
    { assertExposedApi(exposedPrivateApi, "'${moduleName}' Private classes are exposed through API") },
  )
}

private fun assertExposedApi(exposedThrough: Map<String, List<String>>, message: String) {
  if (exposedThrough.isEmpty()) {
    return
  }
  val exposedApiString = buildString {
    for ((exposed, through) in exposedThrough) {
      for (signature in through) {
        appendLine("$exposed <- $signature")
      }
    }
  }
  fail("$message:\n$exposedApiString")
}

internal const val exposedThirdPartyApiFileName = "exposed-third-party-api.txt"
internal const val exposedPrivateApiFileName = "exposed-private-api.txt"