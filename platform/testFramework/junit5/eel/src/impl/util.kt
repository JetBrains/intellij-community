// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.fixture.IsolatedFileSystem
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystemProvider
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.util.io.Ksuid
import com.intellij.util.io.delete
import org.junit.jupiter.api.Assumptions
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.name

internal const val FAKE_WINDOWS_ROOT = "\\\\dummy-ij-root\\test-eel\\"

private val EelPlatform.name: String get() = when (this) {
  is EelPlatform.Posix -> "posix"
  is EelPlatform.Windows -> "windows"
}

internal val currentOs: EelPlatform
  get() = if (SystemInfo.isWindows) {
    EelPlatform.Windows(EelPlatform.Arch.Unknown)
  }
  else {
    EelPlatform.Linux(EelPlatform.Arch.Unknown)
  }

internal fun eelApiByOs(fileSystem: EelUnitTestFileSystem, descriptor: EelTestDescriptor, os: EelPlatform): EelApi {
  return when (os) {
    is EelPlatform.Posix -> EelTestPosixApi(descriptor, fileSystem, fileSystem.fakeLocalRoot)
    is EelPlatform.Windows -> EelTestWindowsApi(descriptor, fileSystem, fileSystem.fakeLocalRoot)
  }
}

internal data class IsolatedFileSystemImpl(override val storageRoot: Path, override val eelDescriptor: EelDescriptor, override val eelApi: EelApi) : IsolatedFileSystem

internal fun eelInitializer(os: EelPlatform): TestFixtureInitializer<IsolatedFileSystem> = TestFixtureInitializer { initialized ->
  checkMultiRoutingFileSystem()
  val meaningfulDirName = "eel-fixture-${os.name}"
  val directory = Files.createTempDirectory(meaningfulDirName)

  val fakeRoot = if (SystemInfo.isUnix) {
    "/eel-test-${directory.name}"
  }
  else {
    "\\\\eel-test\\${directory.name}"
  }

  val defaultProvider = FileSystems.getDefault().provider()

  val service = EelNioBridgeService.getInstanceSync()
  val fakeLocalFileSystem = EelUnitTestFileSystem(EelUnitTestFileSystemProvider(defaultProvider), os, directory, fakeRoot)
  val apiRef = AtomicReference<EelApi>(null)
  val descriptor = EelTestDescriptor(Ksuid.generate().toString(), os, apiRef::get)
  service.register(fakeRoot, descriptor, descriptor.id, true, (os is EelPlatform.Windows)) { _, _ ->
    fakeLocalFileSystem
  }
  val eelApi = eelApiByOs(fakeLocalFileSystem, descriptor, os)
  apiRef.set(eelApi)
  val root = Path.of(fakeRoot)
  initialized(IsolatedFileSystemImpl(root, descriptor, eelApi)) {
    service.unregister(descriptor)
    directory.delete(true)
  }
}

internal fun eelTempDirectoryFixture(fileSystem: TestFixture<IsolatedFileSystem>): TestFixtureInitializer<Path> = TestFixtureInitializer { initialized ->
  val fsdata = fileSystem.init()
  val eelApi = fsdata.eelApi
  val tempDir = eelApi.fs.createTemporaryDirectory().getOrThrow()
  val nioTempDir = tempDir.asNioPath()
  initialized(nioTempDir) {
    nioTempDir.delete(true)
  }
}


internal fun checkMultiRoutingFileSystem() {
  Assumptions.assumeTrue(FileSystems.getDefault().javaClass.name == MultiRoutingFileSystem::class.java.name,
                         "Please enable `-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider`")
}
