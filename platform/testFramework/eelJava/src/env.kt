// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import java.nio.file.Path

internal fun getFileSystemMount(): Path {
  val mount = System.getenv("EEL_FIXTURE_MOUNT")
              ?: throw IllegalArgumentException("The EEL_FIXTURE_MOUNT environment variable is not specified")
  return Path.of(mount)
}

internal fun getFixtureEngine(): EelFixtureEngine {
  val engine = System.getenv("EEL_FIXTURE_ENGINE") ?: return EelFixtureEngine.NONE
  return EelFixtureEngine.valueOf(engine.uppercase())
}

internal fun getEelFixtureEngineJavaHome(): Path {
  val path = System.getenv("EEL_FIXTURE_ENGINE_JAVA_HOME")
         ?: throw IllegalArgumentException("The system environment variable EEL_FIXTURE_ENGINE_JAVA_HOME should be explicitly specified")
  return Path.of(path)
}

internal fun getTeamcityWslJdkDefinition(): Path? {
  return System.getenv("TEAMCITY_WSL_JDK_DEFINITION")?.let { Path.of(it) }
}

internal enum class EelFixtureEngine {
  NONE,
  DOCKER,
  WSL
}