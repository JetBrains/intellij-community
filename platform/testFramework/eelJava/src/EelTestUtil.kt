// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object EelTestUtil {

  fun isEelRequired(): Boolean = !isLocalRun()

  fun isLocalRun(): Boolean = getFixtureEngine() == EelFixtureEngine.NONE

  @Deprecated("The variable EEL_FIXTURE_MOUNT is not needed anymore, use a different path in EEL_FIXTURE_ENGINE_JAVA_HOME")
  fun getFileSystemMount(): @NativePath String {
    val mount = System.getenv("EEL_FIXTURE_MOUNT")
    return mount.orEmpty()
  }

  fun getFixtureEngine(): EelFixtureEngine {
    val engine = System.getenv("EEL_FIXTURE_ENGINE") ?: return EelFixtureEngine.NONE
    return EelFixtureEngine.valueOf(engine.uppercase())
  }

  fun getEelFixtureEngineJavaHome(): @NativePath String {
    val path = System.getenv("EEL_FIXTURE_ENGINE_JAVA_HOME")
               ?: throw IllegalArgumentException("The system environment variable EEL_FIXTURE_ENGINE_JAVA_HOME should be explicitly specified")
    return path
  }

  fun getTeamcityWslJdkDefinition(): @MultiRoutingFileSystemPath Path? {
    return System.getenv("TEAMCITY_WSL_JDK_DEFINITION")?.let { Path.of(it) }
  }

  enum class EelFixtureEngine {
    NONE,
    DOCKER,
    WSL
  }

}