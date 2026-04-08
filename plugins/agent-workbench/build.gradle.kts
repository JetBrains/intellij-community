// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.io.File
import java.util.Properties
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform")
  kotlin("plugin.serialization")
}

// Module JARs must be named after their IntelliJ module ID so that the IDE can resolve content module descriptors
// from lib/modules/<moduleId>.jar. The composedJar task defaults to <rootProject>.<subproject>.jar which doesn't match.
subprojects {
  plugins.withId("org.jetbrains.intellij.platform.module") {
    tasks.named<AbstractArchiveTask>("composedJar") {
      archiveBaseName.set("intellij.agent.workbench.${project.name.replace('-', '.')}")
    }
  }
}

val localProperties = Properties().also { props ->
  File(rootDir, "local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
}
fun localProperty(name: String): String? = localProperties.getProperty(name)

val platformLocalPath: String? = localProperty("platformLocalPath")
val platformVersion = localProperty("platformVersion") ?: "LATEST-EAP-SNAPSHOT"

// Export for subprojects
extra["platformLocalPath"] = platformLocalPath
extra["platformVersion"] = platformVersion

repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
  intellijPlatform {
    defaultRepositories()
    snapshots()
  }
}

intellijPlatform {
  pluginConfiguration {
    name = "Agent Workbench"
    version = localProperty("pluginVersion") ?: "0.1.0"
    ideaVersion {
      sinceBuild = localProperty("pluginSinceBuild")
      untilBuild = localProperty("pluginUntilBuild")
    }
  }
  // Disable bytecode instrumentation when using a local IDE build (262.x compiler tools aren't published yet)
  instrumentCode = false
}

sourceSets {
  main {
    java {
      setSrcDirs(emptyList<String>())
    }
    resources {
      setSrcDirs(listOf("plugin/resources"))
    }
  }
}

dependencies {
  intellijPlatform {
    if (platformLocalPath != null) {
      local(platformLocalPath)
    } else {
      intellijIdeaUltimate(platformVersion) { useInstaller = false }
      bundledPlugins(
        "org.jetbrains.plugins.terminal",
        "org.intellij.plugins.markdown",
      )
      testFramework(TestFrameworkType.Platform)
      testFramework(TestFrameworkType.Bundled)
    }
    jetbrainsRuntime()

    pluginModule(implementation(project(":common")))
    pluginModule(implementation(project(":json")))
    pluginModule(implementation(project(":filewatch")))
    pluginModule(implementation(project(":chat")))
    pluginModule(implementation(project(":container")))
    pluginModule(implementation(project(":prompt-core")))
    pluginModule(implementation(project(":prompt-ui")))
    pluginModule(implementation(project(":prompt-vcs")))
    pluginModule(implementation(project(":prompt-testrunner")))
    pluginModule(implementation(project(":sessions")))
    pluginModule(implementation(project(":sessions-core")))
    pluginModule(implementation(project(":sessions-actions")))
    pluginModule(implementation(project(":sessions-toolwindow")))
    pluginModule(implementation(project(":sessions-launch-config-backend")))
    pluginModule(implementation(project(":claude-common")))
    pluginModule(implementation(project(":claude-sessions")))
    pluginModule(implementation(project(":codex-common")))
    pluginModule(implementation(project(":codex-sessions")))
  }

  // When using local IDE, add bundled plugin JARs directly (bundledPlugins() has a bug with local builds)
  if (platformLocalPath != null) {
    val ideDir = file(platformLocalPath)
    compileOnly(fileTree(ideDir.resolve("plugins/terminal/lib")) { include("**/*.jar") })
    compileOnly(fileTree(ideDir.resolve("plugins/markdown/lib")) { include("**/*.jar") })
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    freeCompilerArgs.addAll("-Xjvm-default=all")
    apiVersion.set(KotlinVersion.KOTLIN_2_3)
    languageVersion.set(KotlinVersion.KOTLIN_2_3)
  }
}

tasks {
  java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  wrapper {
    gradleVersion = "8.14.3"
  }
}
