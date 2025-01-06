// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.platform.testFramework.junit5.eel.fixture.IsolatedFileSystem
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path

@TestApplication
class EelFsShowcase {

  val fsAndEelUnix: TestFixture<IsolatedFileSystem> = eelFixture(EelPath.OS.UNIX)

  val fsAndEelWindows: TestFixture<IsolatedFileSystem> = eelFixture(EelPath.OS.WINDOWS)

  fun EelPath.OS.osDependentFixture(): TestFixture<IsolatedFileSystem> = when (this) {
    EelPath.OS.WINDOWS -> fsAndEelWindows
    EelPath.OS.UNIX -> fsAndEelUnix
  }

  @Test
  fun `local path can be mapped back to eel paths`() {
    val fsdata = fsAndEelUnix.get()
    val localPath = fsdata.storageRoot.resolve("a").resolve("b").resolve("c")
    val eelPath = localPath.asEelPath()
    Assertions.assertEquals("/a/b/c", eelPath.toString())
  }

  @Test
  fun `windows api produces windows paths`() {
    val fsdata = fsAndEelWindows.get()
    val root = fsdata.storageRoot
    val eelPath = root.asEelPath()
    Assertions.assertTrue(OSAgnosticPathUtil.isUncPath(eelPath.toString()))
  }

  @Test
  fun `path mappings are invertible`() {
    val fsdata = fsAndEelUnix.get()
    val nio = fsdata.storageRoot.resolve("a").resolve("b").resolve("c").resolve("d").resolve("e")
    val eel = EelPath.parse("/a/b/c/d/e", fsdata.eelDescriptor)
    val eelNio = nio.asEelPath()
    val nioEel = eel.asNioPath()
    val nioEelNio = eelNio.asNioPath()
    val eelNioEel = nioEel.asEelPath()
    Assertions.assertEquals(nio, nioEel)
    Assertions.assertEquals(nioEel, nioEelNio)
    Assertions.assertEquals(eel, eelNio)
    Assertions.assertEquals(eelNio, eelNioEel)
  }

  @ParameterizedTest
  @EnumSource(EelPath.OS::class)
  fun `uri validity`(os: EelPath.OS) {
    val root = os.osDependentFixture().get().storageRoot
    val path = root.resolve("a/b/c/d/e")
    val uri = EelPathUtils.getUriLocalToEel(path)
    Assertions.assertEquals("file", uri.scheme)
    Assertions.assertNull(uri.authority)
    Assertions.assertTrue(uri.path.contains("a/b/c/d/e"))
  }

  @Test
  fun `windows path is separator agnostic`() {
    val root = fsAndEelWindows.get().storageRoot
    val path = root.resolve("a\\b/c\\d/e")
    val backwardSlashPath = Path.of(path.toString())
    val forwardSlashPath = Path.of(path.toString().replace('\\', '/'))
    Assertions.assertEquals(backwardSlashPath, forwardSlashPath)
    Assertions.assertEquals(backwardSlashPath.asEelPath(), forwardSlashPath.asEelPath())
  }
}