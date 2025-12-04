// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object EelTestUtil {

  fun isEelRequired(): Boolean = !isLocalRun()

  fun isLocalRun(): Boolean = getFixtureEngine() == EelFixtureEngine.NONE

  fun getFileSystemMount(): Path {
    val mount = System.getenv("EEL_FIXTURE_MOUNT")
                ?: throw IllegalArgumentException("The EEL_FIXTURE_MOUNT environment variable is not specified")
    return Path.of(mount)
  }

  fun getFixtureEngine(): EelFixtureEngine {
    val engine = System.getenv("EEL_FIXTURE_ENGINE") ?: return EelFixtureEngine.NONE
    return EelFixtureEngine.valueOf(engine.uppercase())
  }

  fun getEelFixtureEngineJavaHome(): Path {
    val path = System.getenv("EEL_FIXTURE_ENGINE_JAVA_HOME")
               ?: throw IllegalArgumentException("The system environment variable EEL_FIXTURE_ENGINE_JAVA_HOME should be explicitly specified")
    return Path.of(path)
  }

  fun getTeamcityWslJdkDefinition(): Path? {
    return System.getenv("TEAMCITY_WSL_JDK_DEFINITION")?.let { Path.of(it) }
  }

  enum class EelFixtureEngine {
    NONE,
    DOCKER,
    WSL
  }

}