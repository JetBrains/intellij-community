// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform.module")
}

repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
  intellijPlatform {
    defaultRepositories()
    snapshots()
  }
}

intellijPlatform {
  instrumentCode = false
}

sourceSets {
  main {
    java { setSrcDirs(listOf("src")) }
    resources { setSrcDirs(listOf("resources")) }
  }
}

val platformLocalPath = rootProject.extra["platformLocalPath"] as? String
val platformVersion = rootProject.extra["platformVersion"] as String

dependencies {
  intellijPlatform {
    if (platformLocalPath != null) {
      local(platformLocalPath)
    } else {
      intellijIdeaUltimate(platformVersion) { useInstaller = false }
    }
    jetbrainsRuntime()
    if (platformLocalPath == null) {
      bundledModules(
        "intellij.platform.vcs",
      )
      bundledPlugins(
        "org.intellij.plugins.markdown",
      )
    }
  }

  if (platformLocalPath != null) {
    val ideDir = rootProject.file(platformLocalPath)
    compileOnly(fileTree(ideDir.resolve("lib")) {
      include("intellij.platform.vcs*.jar")
    })
    compileOnly(fileTree(ideDir.resolve("plugins/markdown/lib")) { include("**/*.jar") })
  }

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
