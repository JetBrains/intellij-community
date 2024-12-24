// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.fixture

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.testFramework.junit5.eel.impl.currentOs
import com.intellij.platform.testFramework.junit5.eel.impl.eelInitializer
import com.intellij.platform.testFramework.junit5.eel.impl.eelProjectInitializer
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.FileSystem
import java.nio.file.Path


/**
 * Represents an isolated file system, which is accessible to the IDE but not to the OS.
 * In reality, all paths to this file system point to a subtree of the host OS file system.
 */
@TestOnly
interface IsolatedFileSystem {
  /**
   * NIO FileSystem which operates with the path having `eel-test` in their URI scheme
   */
  val fileSystem: FileSystem

  /**
   * EelAPI representing the isolated file system.
   */
  val eelApi: EelApi

  /**
   * A _local_ path pointing to the root of the isolated file system.
   */
  val root: Path
}


/**
 * Creates an instance of Eel which is bound at an isolated directory on the local file system.
 * The local file system would not be able to recognize these paths, so you can test whether your feature is eel-agnostic.
 */
@TestOnly
fun eelFixture(os: EelPath.OS = currentOs): TestFixture<IsolatedFileSystem> {
  return testFixture("eel-test-fixture", eelInitializer(os))
}

/**
 * Creates a project in an isolated directory.
 * The local file system would not be able to recognize the paths of the project.
 */
@TestOnly
fun TestFixture<IsolatedFileSystem>.projectFixture(): TestFixture<Project> {
  return testFixture("eel-project-test-fixture", eelProjectInitializer(this))
}
