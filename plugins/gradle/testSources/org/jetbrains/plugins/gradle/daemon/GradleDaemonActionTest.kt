// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.daemon

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.util.io.toCanonicalPath
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState
import org.jetbrains.plugins.gradle.internal.daemon.getDaemonsStatus
import org.jetbrains.plugins.gradle.internal.daemon.gracefulStopDaemons
import org.jetbrains.plugins.gradle.internal.daemon.stopDaemons
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GradleDaemonActionTest : GradleImportingTestCase() {

  private lateinit var gradleUserHomesToCheck: Set<String>

  override fun setUp() {
    super.setUp()
    // all daemon-related tests should use a custom Gradle User Home to prevent side effects on CI
    overrideGradleUserHome("daemon-test-user-home")
    gradleUserHomesToCheck = setOf(gradleUserHome.toCanonicalPath())
  }

  @Test
  @TargetVersions("6.0+") // The Gradle Daemon below version 6.0 is unstable and causes test fluctuations
  fun `test get daemon status`() {
    runDaemon()
  }

  @Test
  @TargetVersions("6.0+") // The Gradle Daemon below version 6.0 is unstable and causes test fluctuations
  fun `test stop gradle daemon`() {
    runDaemon()
    stopDaemons(gradleUserHomesToCheck)
    // Gradle prior 8.12 uses blocking API for shutdown.
    // As a result, no daemon should be available after stop being executed.
    // Since 8.12, Gradle uses message bus to propagate the stop command.
    if (GradleVersionUtil.isCurrentGradleOlderThan("8.12")) {
      assertNoDaemonRunning()
    }
    else {
      awaitDaemonTermination(30.seconds)
    }
  }

  @Test
  @TargetVersions("6.0+") // The Gradle Daemon below version 6.0 is unstable and causes test fluctuations
  fun `test stop gradle daemon when idle`() {
    runDaemon()
    gracefulStopDaemons(gradleUserHomesToCheck)
    assertNoDaemonRunning()
  }

  private fun runDaemon() {
    assertNoDaemonRunning()
    importProject {
      withJavaPlugin()
    }
    assertDaemonRunning()
  }

  private fun awaitDaemonTermination(duration: Duration) {
    val deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
    while (System.currentTimeMillis() < deadline) {
      if (findDaemon() == null) {
        return
      }
      Thread.sleep(1.seconds.inWholeMilliseconds)
    }
    throw IllegalStateException("Gradle daemon is still running after ${duration.inWholeMilliseconds}ms of waiting for shutdown")
  }

  private fun assertNoDaemonRunning() {
    val activeDaemon = findDaemon()
    assertNull(activeDaemon)
  }

  private fun assertDaemonRunning() {
    val activeDaemon = findDaemon()
    assertNotNull(activeDaemon)
  }

  private fun findDaemon(): DaemonState? {
    val daemons = getDaemonsStatus(gradleUserHomesToCheck)
    if (daemons.isEmpty()) {
      LOG.warn("No running daemons were found in the required user home!")
      return null
    }
    return daemons.find {
      if (gradleVersion != it.version) {
        LOG.info("Found a Gradle Daemon for Gradle Version: ${it.version}")
        return@find false
      }
      val daemonUserHome = it.registryDir?.toPath() ?: throw IllegalStateException("Gradle daemon user home should not be null")
      if (daemonUserHome != gradleUserHome.resolve("daemon")) {
        throw IllegalArgumentException("The Gradle Daemon user home should be in $gradleUserHomesToCheck")
      }
      val daemonJdkHome = it.javaHome?.canonicalPath ?: throw IllegalStateException("Gradle JDK should never be null")
      if (gradleJdkHome != daemonJdkHome) {
        LOG.info("Found a Gradle Daemon with a different JDK home. Expected: ${gradleJdkHome}; Actual: ${daemonJdkHome}")
        return@find false
      }
      return@find true
    }
  }
}