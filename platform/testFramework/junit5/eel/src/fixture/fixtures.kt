// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.fixture

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.testFramework.junit5.eel.impl.currentOs
import com.intellij.platform.testFramework.junit5.eel.impl.eelInitializer
import com.intellij.platform.testFramework.junit5.eel.impl.eelTempDirectoryFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Represents an isolated file system, which is accessible to the IDE but not to the OS.
 */
@TestOnly
interface IsolatedFileSystem {
  /**
   * EelAPI representing the isolated file system.
   */
  val eelApi: EelApi

  /**
   * The descriptor of the created Eel API
   */
  val eelDescriptor: EelDescriptor

  /**
   * A _local_ path pointing to the root of the isolated file system.
   */
  val storageRoot: Path
}

/**
 * Creates an instance of Eel which is bound at an isolated location on the local file system.
 * The local file system would not be able to recognize these paths, so you can test whether your feature is eel-agnostic.
 */
@TestOnly
fun eelFixture(os: EelPlatform = currentOs): TestFixture<IsolatedFileSystem> {
  return testFixture("eel-test-fixture", eelInitializer(os))
}

/**
 * Creates a temporary directory on an environment corresponding to the receiver Eel.
 */
@TestOnly
fun TestFixture<IsolatedFileSystem>.tempDirFixture(): TestFixture<Path> {
  return testFixture("eel-temp-dir-for-project", eelTempDirectoryFixture(this))
}
