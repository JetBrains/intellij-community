// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.parser.dependencies


private val KNOWN_CONFIGURATIONS_IN_ORDER = listOf(
  "feature", "api", "implementation", "compile",
  "testApi", "testImplementation", "testCompile",
  "androidTestApi", "androidTestImplementation", "androidTestCompile", "androidTestUtil")


/**
 * Defined an ordering on gradle configuration names.
 */
@JvmField
val CONFIGURATION_ORDERING = compareBy<String> {
  val result = KNOWN_CONFIGURATIONS_IN_ORDER.indexOf(it)
  if (result != -1) result else KNOWN_CONFIGURATIONS_IN_ORDER.size
}.thenBy { it }