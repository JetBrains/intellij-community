// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.extractFileToCacheLocation
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class GradleInitScriptFunctionalTest {
  companion object {
    private const val MODULE_RELATIVE_PATH = "platform/testFramework/selfContainedProjects"

    private val moduleRoot: Path by lazy {
      Path.of(PathManager.getCommunityHomePath(), MODULE_RELATIVE_PATH)
    }

    fun getInitScript(): Path {
      val fsPath = moduleRoot.resolve("init.gradle.kts")
      require(fsPath.exists()) { "init.gradle.kts not found at $fsPath" }
      return fsPath
    }

    /**
     * Path to the Gradle environment directory in testData (contains gradlew, gradle/wrapper, etc.)
     */
    private val testDataProjectDir: Path by lazy {
      moduleRoot.resolve("testData/gradle")

      //if (BazelRunfiles.isRunningFromBazel) {
      //  // Under Bazel, testData resources are stripped of prefix and available in runfiles
      //  BazelRunfiles.findRunfilesDirectoryUnderCommunityOrUltimate("$MODULE_RELATIVE_PATH/environment/gradle")
      //}
      //else {
      //  moduleRoot.resolve("testData/environment/gradle")
      //}
    }
  }

  @TempDir
  lateinit var projectDir: Path

  @BeforeEach
  fun setup() {
    testDataProjectDir.copyToRecursively(projectDir, followLinks = true, copyAction = { src, dst ->
      if (src.isDirectory() && dst.isDirectory()) return@copyToRecursively CopyActionResult.CONTINUE

      src.copyTo(dst, StandardCopyOption.COPY_ATTRIBUTES)
      CopyActionResult.CONTINUE
    })
  }

  @TempDir
  lateinit var gradleUserHome: Path

  @TempDir
  lateinit var cacheDir: Path
  lateinit var proxy: CachingHttpProxy

  @BeforeEach
  fun startProxy() {
    proxy = CachingHttpProxy(
      cacheDir = cacheDir,
      listen = InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
      offline = false,
    )
  }

  @AfterEach
  fun stopProxy() {
    proxy.close()
  }

  val gradleHome: Path by lazy {
    val communityRoot = BuildDependenciesCommunityRoot(Path(PathManager.getCommunityHomePath()))
    runBlocking {
      val gradleZip = downloadFileToCacheLocation("https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-9.2.1-bin.zip", communityRoot)
      extractFileToCacheLocation(gradleZip, communityRoot, stripRoot = true)
    }
  }

  @Test
  fun gradleProject() {
    assertEquals(0, cacheDir.walk().count())
    assertEquals(0, proxy.fetchCount)
    assertEquals(0, proxy.hitCount)

    // Allow new caching
    proxy.offline = false

    // Cache project dependencies
    runGradleBuild()

    //println("Cache directory contents:")
    //println(cacheDir.walk().joinToString("\n"))

    assertTrue(cacheDir.walk().count() > 100,
               "Expected cache directory to have more than 100 files after build:\n" +
               cacheDir.walk().joinToString("\n") { it.toString() })

    val fetchCount = proxy.fetchCount
    assertThat(fetchCount, greaterThan(50))
    assertEquals(0, proxy.hitCount)

    // Run gradle build with missing proxy, build should succeed
    proxy.offline = true
    gradleUserHome.deleteRecursively()
    runGradleBuild("""
      systemProp.http.proxyHost=127.0.0.1
      systemProp.https.proxyHost=127.0.0.1
      # some random unavailable port
      systemProp.http.proxyPort=8087
      systemProp.https.proxyPort=8087
    """.trimIndent())

    assertFalse(proxy.hadErrors, "Proxy should not have errors, see error messages in test")
    assertEquals(fetchCount, proxy.fetchCount, "Proxy should not have fetched anything new")
    assertEquals(fetchCount, proxy.hitCount, "Proxy should have hit ALL cached files")
  }

  private fun runGradleBuild(gradleProperties: String = "") {
    projectDir.resolve("gradle.properties").writeText(gradleProperties)

    val processBuilder = ProcessBuilder(
      gradleHome.resolve("bin/gradle").toString(),
      "--init-script", getInitScript().toString(),
      "--no-daemon",
      "--stacktrace",
      //"-q",
      "classes"
    )

    @Suppress("IO_FILE_USAGE")
    processBuilder.directory(projectDir.toFile())

    processBuilder.environment()["SELF_CONTAINED_PROXY_URL"] = "http://127.0.0.1:${proxy.port}/proxy"
    processBuilder.environment()["SELF_CONTAINED_VERBOSE"] = "true"
    processBuilder.environment()["GRADLE_HOME"] = gradleHome.toString()
    processBuilder.environment()["GRADLE_USER_HOME"] = gradleUserHome.toString()

    processBuilder.inheritIO()

    val process = processBuilder.start()
    if (!process.waitFor(5, TimeUnit.MINUTES)) {
      error("Gradle build timed out")
    }

    val exitCode = process.exitValue()
    assertEquals(0, exitCode, "Gradle build failed with exit code $exitCode")
  }
}