// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

suspend fun initProject(root: Path, projectInfo: GradleProjectInfo): Path {
  for (compositeInfo in projectInfo.composites) {
    initProject(root, compositeInfo)
  }
  for (moduleInfo in projectInfo.modules) {
    val moduleRoot = root.resolve(moduleInfo.relativePath)
      .createDirectories()
      .refreshAndGetVirtualDirectory()
    moduleInfo.files.createFiles(moduleRoot)
  }
  return root.resolve(projectInfo.projectRelativePath)
}

suspend fun deleteProject(root: Path, projectInfo: GradleProjectInfo) {
  for (compositeInfo in projectInfo.composites) {
    deleteProject(root, compositeInfo)
  }
  withContext(Dispatchers.EDT) {
    edtWriteAction {
      for (moduleInfo in projectInfo.modules) {
        root.resolve(moduleInfo.relativePath)
          .refreshAndFindVirtualFileOrDirectory()
          ?.deleteRecursively()
      }
    }
  }
}

fun gradleProjectInfo(
  gradleVersion: GradleVersion,
  relativePath: String = "project",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleProjectInfoBuilder.() -> Unit = {},
): GradleProjectInfo {
  val projectName = Path.of(relativePath).name
  return GradleProjectInfoBuilderImpl(projectName, relativePath, gradleVersion, gradleDsl)
    .apply(configure)
    .build()
}

fun simpleJavaProjectInfo(
  gradleVersion: GradleVersion,
  relativePath: String = "project",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
): GradleProjectInfo {
  return gradleProjectInfo(gradleVersion, relativePath, gradleDsl) {
    gradleWrapper()
    simpleSettingsFile()
    simpleJavaRootModuleInfo()
  }
}

fun complexJavaProjectInfo(
  gradleVersion: GradleVersion,
  relativePath: String = "project",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
): GradleProjectInfo {
  return gradleProjectInfo(gradleVersion, relativePath, gradleDsl) {
    gradleWrapper()
    simpleSettingsFile {
      include("module")
      includeFlat("$projectName-flat-module")
      includeBuild("../$projectName-composite")
    }
    simpleJavaRootModuleInfo()
    simpleJavaModuleInfo("$projectName.module", "module")
    simpleJavaModuleInfo("$projectName.$projectName-flat-module", "../$projectName-flat-module")
    compositeInfo("$projectName-composite", "../$projectName-composite") {
      simpleSettingsFile()
      simpleJavaRootModuleInfo()
    }
  }
}

fun GradleModuleInfoBuilder.file(relativePath: String, content: String): Unit =
  files.withFile(relativePath, content)

fun GradleModuleInfoBuilder.directory(relativePath: String): Unit =
  files.withDirectory(relativePath)

fun GradleProjectInfoBuilder.gradleWrapper(): Unit =
  files.withGradleWrapper(gradleVersion)

fun GradleModuleInfoBuilder.settingsFile(configure: GradleSettingScriptBuilder<*>.() -> Unit): Unit =
  files.withSettingsFile(gradleVersion, gradleDsl = gradleDsl, configure = configure)

fun GradleModuleInfoBuilder.buildFile(configure: GradleBuildScriptBuilder<*>.() -> Unit): Unit =
  files.withBuildFile(gradleVersion, gradleDsl = gradleDsl, configure = configure)

fun GradleModuleInfoBuilder.simpleSettingsFile(configure: GradleSettingScriptBuilder<*>.() -> Unit = {}): Unit =
  settingsFile {
    setProjectName(name)
    configure()
  }

fun GradleProjectInfoBuilder.simpleJavaRootModuleInfo(): Unit =
  rootModuleInfo(GradleModuleInfoBuilder::configureSimpleJavaModuleInfo)

fun GradleProjectInfoBuilder.simpleJavaModuleInfo(ideName: String, relativePath: String, gradleDsl: GradleDsl? = null): Unit =
  moduleInfo(ideName, relativePath, gradleDsl, GradleModuleInfoBuilder::configureSimpleJavaModuleInfo)

private fun GradleModuleInfoBuilder.configureSimpleJavaModuleInfo() {
  sourceSetInfo("main")
  sourceSetInfo("test")
  buildFile {
    addGroup(groupId)
    addVersion(version)
    withJavaPlugin()
    withJUnit()
  }
}
