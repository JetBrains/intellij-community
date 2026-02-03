// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

object BazelEnvironmentUtil {
  /**
   * Whether the current process is a test run by Bazel.
   *
   * Unfortunately, it's in production code, since some production code
   * needs to know whether it's running in a Bazel test sandbox.
   *
   * Please keep the usage of this method to a minimum.
   *
   * In test code prefer com.intellij.testFramework.common.BazelTestUtil
   */
  @JvmStatic
  fun isBazelTestRun(): Boolean {
    return listOf("TEST_TMPDIR", "RUNFILES_DIR", "JAVA_RUNFILES", "TEST_SRCDIR")
      .all { System.getenv(it) != null } && System.getenv("BAZEL_TEST") == "1"
  }
}