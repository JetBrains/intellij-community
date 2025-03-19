// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleAtLeast
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleOlderThan
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

class GradleJvmResolver(
  private val gradleVersion: GradleVersion,
  private val javaVersionRestriction: JavaVersionRestriction
) {

  private val sdkType = JavaSdk.getInstance()

  private fun isSdkSupported(versionString: String): Boolean {
    val javaVersion = JavaVersion.tryParse(versionString) ?: return false
    return isSdkSupported(javaVersion)
  }

  private fun isSdkSupported(javaVersion: JavaVersion): Boolean {
    return GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)
           && GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)
           && isJavaSupportedByGradleToolingApi(gradleVersion, javaVersion)
           && !javaVersionRestriction.isRestricted(gradleVersion, javaVersion)
  }

  private fun throwSdkNotFoundException(): Nothing {
    val supportedJavaVersions = GradleJvmSupportMatrix.getSupportedJavaVersions(gradleVersion)
    val restrictedJavaVersions = supportedJavaVersions.filter { isSdkSupported(it) }
    val suggestedJavaHomePaths = sdkType.suggestHomePaths().sortedWith(NaturalComparator.INSTANCE)
    throw AssertionError("""
      |Cannot find JDK for $gradleVersion.
      |Please, research JDK restrictions or discuss it with test author, and install JDK manually.
      |Supported JDKs for current Gradle version: $supportedJavaVersions
      |Supported JDKs for current restrictions: $restrictedJavaVersions
      |Checked paths: [
        ${suggestedJavaHomePaths.joinToString("\n") { "|  $it" }}
      |]
      |
    """.trimMargin())
  }

  private fun resolveGradleJvmImpl(parentDisposable: Disposable): Sdk {
    val sdk = findSdkInTable()
              ?: findSdkHomePathOnDisk()
                ?.let { createAndAddSdk(it, parentDisposable) }
              ?: throwSdkNotFoundException()
    println("""
      |
      |Resolved Gradle JVM for the Gradle ${gradleVersion.version}
      |Gradle JVM name: ${sdk.name}
      |Gradle JVM version: ${sdk.versionString}
      |Gradle JVM path: ${sdk.homePath}
      |
    """.trimMargin())
    return sdk
  }

  private fun resolveGradleJvmHomePathImpl(): String {
    val homePath = findSdkInTable()?.homePath
                   ?: findSdkHomePathOnDisk()
                   ?: throwSdkNotFoundException()
    println("""
      |
      |Resolved Gradle JVM for the Gradle ${gradleVersion.version}
      |Gradle JVM version: ${sdkType.getVersionString(homePath)}
      |Gradle JVM path: $homePath
      |
    """.trimMargin())
    return homePath
  }

  private fun isJavaSupportedByGradleToolingApi(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
    // https://github.com/gradle/gradle/issues/9339
    if (isGradleAtLeast(gradleVersion, "5.6") && isGradleOlderThan(gradleVersion, "7.3")) {
      if (javaVersion.feature < 11) {
        return false
      }
    }
    return true
  }

  private fun findSdkInTable(): Sdk? {
    val table = ProjectJdkTable.getInstance()
    return ReadAction.compute(ThrowableComputable { table.allJdks })
      .asSequence()
      .filter { it.versionString != null && isSdkSupported(it.versionString!!) }
      .sortedBy { it.versionString }
      .firstOrNull()
  }

  private fun findSdkHomePathOnDisk(): String? {
    return sdkType.suggestHomePaths().asSequence()
      .filter { sdkType.isValidSdkHome(it) }
      .map { sdkType.getVersionString(it) to it }
      .filter { it.first != null && isSdkSupported(it.first!!) }
      .sortedBy { it.first }
      .map { it.second }
      .firstOrNull()
  }

  private fun createAndAddSdk(sdkHome: String, parentDisposable: Disposable): Sdk? {
    val table = ProjectJdkTable.getInstance()
    val sdk = WriteAction.computeAndWait(ThrowableComputable {
      SdkConfigurationUtil.createAndAddSDK(sdkHome, sdkType)
    })
    if (sdk != null) {
      Disposer.register(parentDisposable, Disposable {
        WriteAction.computeAndWait(ThrowableComputable {
          table.removeJdk(sdk)
        })
      })
    }
    return sdk
  }

  companion object {

    @JvmStatic
    fun resolveGradleJvm(gradleVersion: GradleVersion, parentDisposable: Disposable, javaVersionRestriction: JavaVersionRestriction): Sdk {
      return GradleJvmResolver(gradleVersion, javaVersionRestriction)
        .resolveGradleJvmImpl(parentDisposable)
    }

    @JvmStatic
    fun resolveGradleJvmHomePath(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction): String {
      return GradleJvmResolver(gradleVersion, javaVersionRestriction)
        .resolveGradleJvmHomePathImpl()
    }
  }
}