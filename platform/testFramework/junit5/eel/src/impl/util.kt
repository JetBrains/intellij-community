// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.fixture.IsolatedFileSystem
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystemProvider
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.util.io.Ksuid
import com.intellij.util.io.delete
import org.junit.jupiter.api.Assumptions
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.name

private val EelPlatform.name: String
  get() = when (this) {
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

  val fakeLocalFileSystem = EelUnitTestFileSystem(EelUnitTestFileSystemProvider(defaultProvider), os, directory, fakeRoot)
  val apiRef = AtomicReference<EelApi>(null)
  val id = Ksuid.generate()
  val descriptor = EelTestDescriptor(Path(fakeRoot), id, os.osFamily)

  // EelDescriptor has almost static lifetime by contract, so we bind it to the application service
  val disposable: Disposable = ApplicationManager.getApplication().service<TestEelService>()

  MultiRoutingFileSystemBackend.EP_NAME.point.registerExtension(
    object : MultiRoutingFileSystemBackend {
      override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? =
        if (sanitizedPath.startsWith(fakeRoot.replace("\\", "/")))
          fakeLocalFileSystem
        else
          null

      override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> =
        fakeLocalFileSystem.rootDirectories.map { it.toString() }

      override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> =
        fakeLocalFileSystem.fileStores?.filterNotNull() ?: listOf()
    },
    disposable,
  )

  val machine: EelMachine = object : EelMachine {
    override val internalName: String = "mock-$id"
    override suspend fun toEelApi(descriptor: EelDescriptor): EelApi = apiRef.get()
    override fun ownsPath(path: Path): Boolean {
      return path.getEelDescriptor() == descriptor
    }
  }

  EelMachineResolver.EP_NAME.point.registerExtension(object : EelMachineResolver {
    override fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
      return if (eelDescriptor == descriptor) machine else null
    }

    override suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
      return getResolvedEelMachine(eelDescriptor)
    }

    override suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine? {
      return if (internalName == machine.internalName) machine else null
    }
  }, disposable)

  EelProvider.EP_NAME.point.registerExtension(
    object : EelProvider {
      override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
        return if (getEelDescriptor(Path(path)) == descriptor) machine else null
      }

      override fun getEelDescriptor(path: @MultiRoutingFileSystemPath Path): EelDescriptor? =
        if (path.startsWith(Path.of(fakeRoot))) descriptor
        else null

      override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? =
        if (eelDescriptor == descriptor) listOf(fakeRoot)
        else null
    },
    disposable,
  )

  val eelApi = eelApiByOs(fakeLocalFileSystem, descriptor, os)
  apiRef.set(eelApi)
  val root = Path.of(fakeRoot)

  // We can't dispose descriptor after each test because EelMachineResolver must be available till the end of the app
  Disposer.register(disposable) {
    directory.delete(true)
  }

  initialized(IsolatedFileSystemImpl(root, descriptor, eelApi)) {
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
  Assumptions.assumeTrue(FileSystems.getDefault() is MultiRoutingFileSystem,
                         "Please enable `-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider`")
}

/**
 * Used to dispose test eels
 */
@Service(Service.Level.APP)
private class TestEelService : Disposable.Default