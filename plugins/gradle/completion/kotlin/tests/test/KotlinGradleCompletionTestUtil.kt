// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.gradle.testFramework.util.writeTextAndCommit
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestLookupElementPresentation
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME

private const val CUSTOM_CATALOG_PATH = "customPath/customLibs.toml"

/**
 * Writes the entries of the version catalogs of the project.
 * [libs] is the text of the default catalog `gradle/libs.versions.toml`.
 * [customLibs] is the text of the catalog `customLibs`. With [customLibs], the settings script declares that catalog.
 */
internal fun JavaCodeInsightTestFixture.writeVersionCatalogs(libs: String? = null, customLibs: String? = null) {
  if (libs != null) {
    project.writeTextAndCommit("gradle/libs.versions.toml", libs)
  }
  if (customLibs != null) {
    project.writeTextAndCommit(CUSTOM_CATALOG_PATH, customLibs)
    project.writeTextAndCommit(
      KOTLIN_DSL_SETTINGS_FILE_NAME,
      """
      dependencyResolutionManagement {
          dependencyResolutionManagement {
              versionCatalogs {
                  create("customLibs") { from(files("$CUSTOM_CATALOG_PATH")) }
              }
          }
      }
      """.trimIndent()
    )
  }
}

/**
 * Returns the texts of the lookup items in the order that the lookup shows them.
 * The list is empty when there is no lookup.
 */
internal fun JavaCodeInsightTestFixture.suggestions(): List<String> =
  lookupElements.orEmpty().map { TestLookupElementPresentation.renderReal(it).itemText ?: it.lookupString }

/**
 * Inserts the lookup item at [index].
 */
internal fun JavaCodeInsightTestFixture.applySuggestion(index: Int) {
  val elementToApply = lookupElements?.getOrNull(index) ?: error("There is no suggestion with index $index")
  lookup.currentItem = elementToApply
  finishLookup(Lookup.REPLACE_SELECT_CHAR)
}

/**
 * Runs [test] in the dumb mode when [runInDumbMode] is true, and in the smart mode otherwise.
 */
internal fun JavaCodeInsightTestFixture.runInDumbModeIf(runInDumbMode: Boolean, test: () -> Unit) {
  if (runInDumbMode) {
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      test()
    }
  }
  else {
    test()
  }
}

/**
 * Replaces the escape sequences that keep a colon or a comma of a test argument away from the argument parser.
 */
internal fun String.unescape(): String = this
  .replace("<colon>", ":")
  .replace("<comma>", ",")
