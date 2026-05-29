// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.buildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.settingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

suspend fun GradleProjectInfo.initProject(root: Path): Path {
  for (compositeInfo in composites) {
    compositeInfo.initProject(root)
  }
  for (moduleInfo in modules) {
    val moduleRoot = root.resolve(moduleInfo.relativePath)
      .createDirectories()
      .refreshAndGetVirtualDirectory()
    moduleInfo.files.createFiles(moduleRoot)
  }
  return root.resolve(projectRelativePath)
}

suspend fun GradleProjectInfo.deleteProject(root: Path) {
  for (compositeInfo in composites) {
    compositeInfo.deleteProject(root)
  }
  withContext(Dispatchers.EDT) {
    edtWriteAction {
      for (moduleInfo in modules) {
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
): GradleProjectInfo =
  gradleProjectInfo(gradleVersion, Path.of(relativePath).name, relativePath, gradleDsl, configure)

fun gradleProjectInfo(
  gradleVersion: GradleVersion,
  projectName: String,
  relativePath: String,
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleProjectInfoBuilder.() -> Unit = {},
): GradleProjectInfo =
  GradleProjectInfoBuilderImpl(projectName, relativePath, gradleVersion, gradleDsl)
    .apply(configure)
    .build()

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

fun multiModuleProjectInfo(
  gradleVersion: GradleVersion,
  relativePath: String = "project",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  useBuildSrc: Boolean = GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.0"),
  includeProjectsWithDuplicatedNames: Boolean = false,
): GradleProjectInfo =
  gradleProjectInfo(gradleVersion, "rootProjectName", relativePath, gradleDsl) {
    settingsFile {
      setProjectName("rootProjectName")
      include("module")
      includeBuild("../includedProject")
      if (includeProjectsWithDuplicatedNames) {
        includeBuild("../includedProject2")
      }
    }
    simpleJavaRootModuleInfo()
    simpleJavaModuleInfo("rootProjectName.module", "module", groupId = "moduleGroup", version = "1.0")
    if (useBuildSrc) {
      moduleInfo("rootProjectName.buildSrc", "buildSrc") {
        sourceSetInfo("main")
        sourceSetInfo("test")
        buildFile {
          withPlugin("groovy")
          addImplementationDependency(code("gradleApi()"))
          addImplementationDependency(code("localGroovy()"))
        }
      }
    }

    val includedProjectIdeName = when (GradleVersionUtil.isGradleAtLeast(gradleVersion, "6.0")) {
      true -> "includedProject"
      else -> "includedProjectName"
    }
    compositeInfo(includedProjectIdeName, "../includedProject") {
      settingsFile {
        setProjectName("includedProjectName")
        include("module")
      }
      simpleJavaRootModuleInfo(groupId = "includedProjectGroup", version = "1.0")
      simpleJavaModuleInfo("$includedProjectIdeName.module", "module", groupId = "includedProjectModuleGroup", version = "1.0")
    }
    if (includeProjectsWithDuplicatedNames) {
      compositeInfo("includedProject2", "../includedProject2") {
        settingsFile {
          setProjectName("includedProjectName")
          include("module2")
        }
        simpleJavaRootModuleInfo(groupId = "includedProject2Group", version = "1.0")
        simpleJavaModuleInfo("includedProject2.module2", "module2", groupId = "includedProject2ModuleGroup", version = "1.0")
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

fun GradleProjectInfoBuilder.simpleJavaRootModuleInfo(
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit = {},
): Unit =
  rootModuleInfo {
    configureSimpleJavaModuleInfo(groupId, version, configureBuildFile)
  }

fun GradleProjectInfoBuilder.simpleJavaModuleInfo(
  ideName: String,
  relativePath: String,
  gradleDsl: GradleDsl? = null,
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit = {},
): Unit =
  moduleInfo(ideName, relativePath, gradleDsl) {
    configureSimpleJavaModuleInfo(groupId, version, configureBuildFile)
  }

fun GradleModuleInfoBuilder.configureSimpleJavaModuleInfo(
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit = {},
) {
  if (groupId != null) {
    this.groupId = groupId
  }
  if (version != null) {
    this.version = version
  }
  sourceSetInfo("main")
  sourceSetInfo("test")
  buildFile {
    addGroup(this@configureSimpleJavaModuleInfo.groupId)
    addVersion(this@configureSimpleJavaModuleInfo.version)
    withJavaPlugin()
    withJUnit()
    configureBuildFile()
  }
}

fun GradleProjectInfoBuilder.simpleKotlinDslRootModuleInfo(
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit,
): Unit = rootModuleInfo {
  configureSimpleKotlinDslRootModuleInfo(groupId, version, configureBuildFile)
}

fun GradleProjectInfoBuilder.simpleKotlinDslModuleInfo(
  ideName: String,
  relativePath: String,
  gradleDsl: GradleDsl? = null,
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit,
): Unit = moduleInfo(ideName, relativePath, gradleDsl) {
  configureSimpleKotlinDslRootModuleInfo(groupId, version, configureBuildFile)
}

fun GradleModuleInfoBuilder.configureSimpleKotlinDslRootModuleInfo(
  groupId: String? = null,
  version: String? = null,
  configureBuildFile: GradleBuildScriptBuilder<*>.() -> Unit,
) {
  if (groupId != null) {
    this.groupId = groupId
  }
  if (version != null) {
    this.version = version
  }
  sourceSetInfo("main")
  sourceSetInfo("test")
  buildFile {
    addGroup(this@configureSimpleKotlinDslRootModuleInfo.groupId)
    addVersion(this@configureSimpleKotlinDslRootModuleInfo.version)
    withKotlinDsl()
    configureBuildFile()
  }
}

val GradleModuleInfo.settingsScriptName: String
  get() = gradleDsl.settingsScriptName

val GradleModuleInfo.buildScriptName: String
  get() = gradleDsl.buildScriptName
