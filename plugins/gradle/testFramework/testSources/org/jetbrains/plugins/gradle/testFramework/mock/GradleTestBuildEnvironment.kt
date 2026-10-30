// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.mock

import com.intellij.testFramework.common.mock.requireImplemented
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.gradle.tooling.model.build.JavaEnvironment
import java.io.File
import java.nio.file.Path

interface GradleTestBuildEnvironment : BuildEnvironment {

  fun withProjectRoot(projectRoot: Path): GradleTestBuildEnvironment
  fun withBuildRootDir(buildRootDir: Path): GradleTestBuildEnvironment
  fun withGradleUserHome(gradleUserHome: Path): GradleTestBuildEnvironment
  fun withGradleVersion(gradleVersion: String): GradleTestBuildEnvironment
  fun withVersionInfo(renderedVersionInfo: String): GradleTestBuildEnvironment
  fun withJavaHome(javaHome: Path): GradleTestBuildEnvironment
  fun withJvmArguments(jvmArguments: List<String>): GradleTestBuildEnvironment

  companion object {
    fun createBuildEnvironment(): GradleTestBuildEnvironment =
      GradleTestBuildEnvironmentImpl()
  }
}

private data class GradleTestBuildEnvironmentImpl(
  private val buildRootDir: Path? = null,
  private val gradleUserHome: Path? = null,
  private val gradleVersion: String? = null,
  private val versionInfo: String? = null,
  private val javaHome: Path? = null,
  private val jvmArguments: List<String>? = null,
) : GradleTestBuildEnvironment {

  override fun withProjectRoot(projectRoot: Path) = copy(buildRootDir = projectRoot)
  override fun withBuildRootDir(buildRootDir: Path) = copy(buildRootDir = buildRootDir)
  override fun withGradleUserHome(gradleUserHome: Path) = copy(gradleUserHome = gradleUserHome)
  override fun withGradleVersion(gradleVersion: String) = copy(gradleVersion = gradleVersion)
  override fun withVersionInfo(renderedVersionInfo: String) = copy(versionInfo = renderedVersionInfo)
  override fun withJavaHome(javaHome: Path) = copy(javaHome = javaHome)
  override fun withJvmArguments(jvmArguments: List<String>) = copy(jvmArguments = jvmArguments)

  override fun getBuildIdentifier(): BuildIdentifier =
    BuildIdentifier { File(requireImplemented(GradleTestBuildEnvironmentImpl::buildRootDir).toString()) }

  override fun getGradle(): GradleEnvironment = object : GradleEnvironment {
    override fun getGradleVersion(): String = requireImplemented(GradleTestBuildEnvironmentImpl::gradleVersion)
    override fun getGradleUserHome(): File = File(requireImplemented(GradleTestBuildEnvironmentImpl::gradleUserHome).toString())
  }

  override fun getJava(): JavaEnvironment = object : JavaEnvironment {
    override fun getJavaHome(): File = File(requireImplemented(GradleTestBuildEnvironmentImpl::javaHome).toString())
    override fun getJvmArguments(): List<String> = requireImplemented(GradleTestBuildEnvironmentImpl::jvmArguments)
  }

  override fun getVersionInfo(): String = requireImplemented(GradleTestBuildEnvironmentImpl::versionInfo)
}