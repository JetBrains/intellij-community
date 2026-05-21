// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform.module")
  kotlin("plugin.serialization")
}

repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
  intellijPlatform {
    defaultRepositories()
    snapshots()
    nightly()
  }
}

intellijPlatform {
  instrumentCode = false
}

val ultimateModuleDir = rootProject.file("../../../plugins/agent-workbench/ai-review-agents")

sourceSets {
  main {
    java { setSrcDirs(listOf(ultimateModuleDir.resolve("src"))) }
    resources { setSrcDirs(listOf(ultimateModuleDir.resolve("resources"))) }
  }
}

val platformLocalPath = rootProject.extra["platformLocalPath"] as? String
val platformVersion = rootProject.extra["platformVersion"] as String

val llmPluginDir: File? = rootProject.findProperty("llmPluginPath")?.toString()?.let { rootProject.file(it) }
  ?: rootProject.file("../../../out/deploy/temp/non-bundled-plugins-IU/ml-llm").takeIf { it.isDirectory }

dependencies {
  intellijPlatform {
    if (platformLocalPath != null) {
      local(platformLocalPath)
    } else {
      intellijIdeaUltimate(platformVersion) { useInstaller = false }
      bundledModules(
        "intellij.platform.vcs",
        "intellij.platform.vcs.impl",
      )
      bundledPlugins(
        "Git4Idea",
      )
    }
    jetbrainsRuntime()
  }

  if (platformLocalPath != null) {
    val ideDir = rootProject.file(platformLocalPath)
    compileOnly(fileTree(ideDir.resolve("lib")) {
      include("intellij.platform.vcs*.jar")
    })
    compileOnly(fileTree(ideDir.resolve("plugins/vcs-git/lib")) { include("**/*.jar") })
  }

  if (llmPluginDir != null) {
    compileOnly(fileTree(llmPluginDir.resolve("lib")) { include("**/*.jar") })
  }

  implementation(project(":ai-review"))
  implementation(project(":prompt-core"))
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    freeCompilerArgs.addAll("-Xjvm-default=all")
    apiVersion.set(KotlinVersion.KOTLIN_2_3)
    languageVersion.set(KotlinVersion.KOTLIN_2_3)
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}
