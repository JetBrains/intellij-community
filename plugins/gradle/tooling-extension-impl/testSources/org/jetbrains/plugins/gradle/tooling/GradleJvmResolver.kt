// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.ide.starter.sdk.JdkVersion
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.junit.jupiter.api.assertNotNull

object GradleJvmResolver {

  @JvmStatic
  fun resolveGradleJvm(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction, parentDisposable: Disposable): Sdk =
    resolveGradleSdkObject(gradleVersion, javaVersionRestriction)
      .asSdk(parentDisposable)

  @JvmStatic
  fun resolveGradleJvmHomePath(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction): String =
    resolveGradleSdkObject(gradleVersion, javaVersionRestriction).getPath()

  private fun resolveGradleSdkObject(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction): SdkObject {
    val compatibleJdks = suggestCompatibleJdks(gradleVersion, javaVersionRestriction)
    println("Java versions: $compatibleJdks chosen as compatible ones for running Gradle: $gradleVersion")
    val targetJavaVersion = compatibleJdks.firstOrNull()
                            ?: throw IllegalStateException("Unable to find a compatible JDK for running Gradle: $gradleVersion")
    val sdk = JdkDownloaderFacade.jdkDownloader(targetJavaVersion.number.toString())
      .toSdk()
    println(
      """
        |Gradle: $gradleVersion
        |Gradle JVM version: ${sdk.sdkName}
        |Gradle JVM path: ${sdk.sdkPath}
        |Gradle JVM type: ${sdk.sdkType}
        |
      """.trimMargin()
    )
    return sdk
  }

  private fun suggestCompatibleJdks(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction): List<JdkVersion> =
    GradleJvmSupportMatrix.getSupportedJavaVersions(gradleVersion)
      .filter { isSdkSupported(it, gradleVersion, javaVersionRestriction) }
      .map { "JDK_${it.feature}" }
      .map { runCatching { JdkVersion.valueOf(it) } }
      .mapNotNull { it.getOrNull() }

  private fun isSdkSupported(
    javaVersion: JavaVersion,
    gradleVersion: GradleVersion,
    javaVersionRestriction: JavaVersionRestriction,
  ): Boolean = GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)
               && GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)
               && !javaVersionRestriction.isRestricted(gradleVersion, javaVersion)

  private fun SdkObject.getPath() = sdkPath.toString()

  private fun SdkObject.asSdk(parentDisposable: Disposable): Sdk {
    val table = ProjectJdkTable.getInstance()
    val sdk = WriteAction.computeAndWait(ThrowableComputable {
      SdkConfigurationUtil.createAndAddSDK(sdkPath.toString(), SdkType.findByName(sdkType)!!)
    })
    assertNotNull(sdk, "SDK should be added into the project jdk table")
    Disposer.register(parentDisposable, Disposable {
      WriteAction.computeAndWait(ThrowableComputable {
        table.removeJdk(sdk)
      })
    })
    return sdk
  }
}
