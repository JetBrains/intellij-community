// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl.graldeJvm

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

class GradleJvmResolver(private val gradleVersion: GradleVersion) {

  private val sdkType = JavaSdk.getInstance()

  private fun isSdkSupported(versionString: String): Boolean {
    val javaVersion = JavaVersion.tryParse(versionString) ?: return false
    return GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)
           && GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)
  }

  private fun throwSdkNotFoundException(): Nothing {
    val supportedJavaVersions = GradleJvmSupportMatrix.getSupportedJavaVersions(gradleVersion)
    val suggestedJavaHomePaths = sdkType.suggestHomePaths().sortedWith(NaturalComparator.INSTANCE)
    throw AssertionError("""
      |Cannot find JDK for $gradleVersion.
      |Please, research JDK restrictions or discuss it with test author, and install JDK manually.
      |Supported JDKs for current restrictions: $supportedJavaVersions
      |Checked paths: [
        ${suggestedJavaHomePaths.joinToString("\n") { "|  $it" }}
      |]
      |
    """.trimMargin())
  }

  private fun resolveGradleJvmImpl(parentDisposable: Disposable): Sdk {
    return findSdkInTable()
           ?: findSdkHomePathOnDisk()
             ?.let { createAndAddSdk(it, parentDisposable) }
           ?: throwSdkNotFoundException()
  }

  private fun resolveGradleJvmHomePathImpl(): String {
    return findSdkInTable()
             ?.homePath
           ?: findSdkHomePathOnDisk()
           ?: throwSdkNotFoundException()
  }

  private fun findSdkInTable(): Sdk? {
    val table = ProjectJdkTable.getInstance()
    return runReadAction { table.allJdks }.asSequence()
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
    val sdk = runInEdtAndGet {
      SdkConfigurationUtil.createAndAddSDK(sdkHome, sdkType)
    }
    if (sdk != null) {
      Disposer.register(parentDisposable, Disposable {
        runWriteActionAndWait {
          table.removeJdk(sdk)
        }
      })
    }
    return sdk
  }

  companion object {

    @JvmStatic
    fun resolveGradleJvm(gradleVersion: GradleVersion, parentDisposable: Disposable): Sdk {
      return GradleJvmResolver(gradleVersion).resolveGradleJvmImpl(parentDisposable)
    }

    @JvmStatic
    fun resolveGradleJvmHomePath(gradleVersion: GradleVersion): String {
      return GradleJvmResolver(gradleVersion).resolveGradleJvmHomePathImpl()
    }
  }
}