// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.build

import com.intellij.mock.MockVirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test

import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Run as a part of CLion Fast Tests
 */
@RunWith(JUnit4::class)
class BazelFilesTypeUsageSchemaDescriptorTest: LightPlatform4TestCase() {
  @Test
  fun testDotBazelFiles() {
    val descriptor = DotBazelFileTypeUsageSchemaDescriptor()

    assertTrue(descriptor.describes(project, MockVirtualFile("foo.bazel")))
    assertTrue(descriptor.describes(project, MockVirtualFile("foo.bzl")))
    assertTrue(descriptor.describes(project, MockVirtualFile("foo.BAZEL")))
    assertTrue(descriptor.describes(project, MockVirtualFile("foo.BZL")))
    assertFalse(descriptor.describes(project, MockVirtualFile("BUILD.BZL")))
    assertFalse(descriptor.describes(project, MockVirtualFile("BUILD")))
    assertFalse(descriptor.describes(project, MockVirtualFile("BUILD.bazel")))
    assertFalse(descriptor.describes(project, MockVirtualFile("WORKSPACE")))
  }

  @Test
  fun testBazelBuildFiles() {
    val descriptor = BazelBuildFileTypeUsageSchemaDescriptor()

    assertTrue(descriptor.describes(project, MockVirtualFile("BUILD.bazel")))
    assertTrue(descriptor.describes(project, MockVirtualFile("BUILD.bzl")))
    assertTrue(descriptor.describes(project, MockVirtualFile("BUILD")))
    assertFalse(descriptor.describes(project, MockVirtualFile("build.bazel")))
  }

  @Test
  fun testBazelWorkspaceFiles() {
    val descriptor = BazelWorkspaceFileTypeUsageSchemaDescriptor()

    assertTrue(descriptor.describes(project, MockVirtualFile("WORKSPACE")))
    assertFalse(descriptor.describes(project, MockVirtualFile("workspace")))
    assertFalse(descriptor.describes(project, MockVirtualFile("BUILD")))
  }

  @Test
  fun testBazelModuleFiles() {
    val descriptor = BazelModuleFileTypeUsageSchemaDescriptor()

    assertTrue(descriptor.describes(project, MockVirtualFile("MODULE.bazel")))
    assertFalse(descriptor.describes(project, MockVirtualFile("module.bazel")))
    assertFalse(descriptor.describes(project, MockVirtualFile("WORKSPACE")))
  }
}
