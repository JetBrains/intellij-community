// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@Suppress("GrUnresolvedAccess")
open class GradleOutputParsersMessagesImportingTestCase : BuildViewMessagesImportingTestCase() {

  var enableStackTraceImportingOption = false
  var quietLogLevelImportingOption = false

  override fun setUp() {
    super.setUp()
    enableStackTraceImportingOption = false
    quietLogLevelImportingOption = false
  }

  // do not inject repository
  override fun injectRepo(config: String): String = config

  override fun createImportSpec(): ImportSpec {
    val baseImportSpec = super.createImportSpec()
    val baseArguments = baseImportSpec.arguments
    val importSpecBuilder = ImportSpecBuilder(baseImportSpec)
    if (enableStackTraceImportingOption) {
      if (baseArguments == null || !baseArguments.contains("--stacktrace")) {
        importSpecBuilder.withArguments("${baseArguments} --stacktrace")
      }
    }
    else {
      if (baseArguments != null) {
        importSpecBuilder.withArguments(baseArguments.replace("--stacktrace", ""))
      }
    }
    if (quietLogLevelImportingOption) {
      if (baseArguments == null || !baseArguments.contains("--quiet")) {
        importSpecBuilder.withArguments("${baseArguments} --quiet")
      }
    }
    return importSpecBuilder.build()
  }

  companion object {

    @JvmStatic
    protected val MAVEN_REPOSITORY = if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      "https://repo.labs.intellij.net/repo1"
    }
    else {
      "https://repo1.maven.org/maven2"
    }

    @JvmStatic
    protected fun ScriptTreeBuilder.mavenRepository(url: String, useOldStyleMetadata: Boolean) {
      call("maven") {
        assign("url", url)
        if (useOldStyleMetadata) {
          call("metadataSources") {
            call("mavenPom")
            call("artifact")
          }
        }
      }
    }
  }
}
