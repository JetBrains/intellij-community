// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.junit.Test
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import kotlin.test.assertEquals as kAssertEquals
import kotlin.test.assertNotNull as kAssertNotNull

private const val TARGET_GRADLE_VERSION = "8.13"
private const val COMMON_MAIN = "commonMain"
private const val ANDROID_MAIN = "androidMain"
private const val IOS_MAIN = "iosMain"

private val SOURCE_SETS = setOf(COMMON_MAIN, ANDROID_MAIN, IOS_MAIN)

@TestRoot("../../../community/plugins/compose/intellij.compose.ide.plugin.resources/testData")
@TestMetadata("")
class ComposeResourcesGradleImportTest : KotlinGradleImportingTestCase() {

  @Parameter(1)
  lateinit var sourceSetName: String

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test default composeResources`() = doTestWithComposeResourcesModel { composeResourcesModel ->
    kAssertEquals("Res", composeResourcesModel.nameOfResClass)
    kAssertEquals(false, composeResourcesModel.isPublicResClass)

    assertNotEmpty(composeResourcesModel.customComposeResourcesDirs.entries)
    val composeResources = composeResourcesModel.customComposeResourcesDirs[sourceSetName]
    kAssertNotNull(composeResources)
    val (directoryPath, isCustom) = composeResources
    assertTrue(directoryPath.endsWith("src/$sourceSetName/composeResources"))
    kAssertEquals(false, isCustom)
  }


  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test custom composeResources`() = doTestWithComposeResourcesModel(
    """
        nameOfResClass = "CustomRes"
        publicResClass = true
        customDirectory(
          sourceSetName = "${sourceSetName}",
          directoryProvider = provider { layout.projectDirectory.dir("customDir") }
        )
        
       """.trimIndent()
  ) { composeResourcesModel ->
    kAssertEquals("CustomRes", composeResourcesModel.nameOfResClass)
    kAssertEquals(true, composeResourcesModel.isPublicResClass)
    assertNotEmpty(composeResourcesModel.customComposeResourcesDirs.entries)

    val composeResources = composeResourcesModel.customComposeResourcesDirs[sourceSetName]
    kAssertNotNull(composeResources)
    assertTrue(composeResources.first.endsWith("composeApp/customDir"))
    assertTrue(composeResources.second)

    SOURCE_SETS.minus(sourceSetName).forEach { sourceSetName ->
      val composeResourcesModel = composeResourcesModel.customComposeResourcesDirs[sourceSetName]
      kAssertNotNull(composeResourcesModel)
      assertTrue(composeResourcesModel.first.endsWith("src/${sourceSetName}/composeResources"))
      assertFalse(composeResourcesModel.second)
    }
  }

  private fun doTestWithComposeResourcesModel(config: String = "", block: (ComposeResourcesModel) -> Unit) {
    importProjectFromTestData(config)

    val module = ModuleManager.getInstance(project).findModuleByName("ComposeResources.composeApp.commonMain")
    kAssertNotNull(module)

    val moduleData = GradleUtil.findGradleModuleData(module)
    kAssertNotNull(moduleData)

    val composeResourcesModel = ExternalSystemApiUtil.find(moduleData, COMPOSE_RESOURCES_KEY)?.data
    kAssertNotNull(composeResourcesModel)

    block(composeResourcesModel)
  }

  private fun importProjectFromTestData(config: String): List<VirtualFile> {
    val files = importProjectFromTestData()
    val composeAppBuildFile = files.first { it.path.endsWith("composeApp/build.gradle.kts") }
    val content = buildString {
      append(composeAppBuildFile.readText())
      append("compose.resources { $config }")
    }
    runWriteAction { composeAppBuildFile.writeText(content) }
    importProject()
    return files
  }

  private fun <R> runWriteAction(update: () -> R): R =
    WriteCommandAction.runWriteCommandAction(myProject, Computable { update() })

  companion object {
    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameters(name = "{index}: source set {1} with Gradle-{0}")
    fun data(): Collection<Any> = SOURCE_SETS.map { arrayOf(TARGET_GRADLE_VERSION, it) }
  }
}

