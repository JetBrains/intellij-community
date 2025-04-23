// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.impl.fs.PosixNioBasedEelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.toEelArch
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.util.system.CpuArch
import java.nio.file.Files
import java.nio.file.Path

internal class EelTestPosixApi(override val descriptor: EelTestDescriptor, fileSystem: EelUnitTestFileSystem, localPrefix: String) : EelPosixApi {
  override val userInfo: EelUserPosixInfo = EelTestPosixUserInfo(descriptor)

  override val fs: PosixNioBasedEelFileSystemApi = EelTestFileSystemPosixApi(descriptor, fileSystem)

  override val archive: EelArchiveApi
    get() = TODO()
  override val tunnels: EelTunnelsPosixApi
    get() = TODO()
  override val exec: EelExecApi
    get() = object : EelExecApi {
      override val descriptor: EelDescriptor get() = this@EelTestPosixApi.descriptor
      override suspend fun execute(generatedBuilder: EelExecApi.ExecuteProcessOptions) = TODO()
      override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = emptyMap()
      override suspend fun findExeFilesInPath(binaryName: String) = TODO()
    }

}

private class EelTestFileSystemPosixApi(override val descriptor: EelTestDescriptor, fileSystem: EelUnitTestFileSystem) : PosixNioBasedEelFileSystemApi(fileSystem, EelTestPosixUserInfo(descriptor)) {

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> {
    TODO("Not yet implemented")
  }

  override suspend fun createTemporaryDirectory(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
    return wrapIntoEelResult {
      val nioTempDir = Files.createTempDirectory(fs.rootDirectories.single(), options.prefix)
      Path.of(nioTempDir.toString()).asEelPath()
    }
  }

  override suspend fun createTemporaryFile(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
    TODO("Not yet implemented")
  }
}

private class EelTestPosixUserInfo(descriptor: EelTestDescriptor) : EelUserPosixInfo {
  override val uid: Int
    get() = 1001
  override val gid: Int
    get() = 1
  override val home: EelPath = EelPath.parse("/home", descriptor)
}