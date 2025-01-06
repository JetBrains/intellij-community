// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelArchiveApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsPosixApi
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.impl.fs.PosixNioBasedEelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.util.system.CpuArch
import java.nio.file.Files

internal class EelTestPosixApi(override val descriptor: EelTestDescriptor, fileSystem: EelUnitTestFileSystem, localPrefix: String) : EelPosixApi {
  override val userInfo: EelUserPosixInfo = EelTestPosixUserInfo(descriptor)

  override val platform: EelPlatform.Posix
    get() = if (CpuArch.CURRENT == CpuArch.ARM64) {
      EelPlatform.Aarch64Linux
    }
    else {
      EelPlatform.X8664Linux
    }

  override val fs: PosixNioBasedEelFileSystemApi = EelTestFileSystemPosixApi(descriptor, fileSystem)

  override val archive: EelArchiveApi
    get() = TODO()
  override val tunnels: EelTunnelsPosixApi
    get() = TODO()
  override val exec: EelExecApi
    get() = TODO()

}

private class EelTestFileSystemPosixApi(override val descriptor: EelTestDescriptor, fileSystem: EelUnitTestFileSystem) : PosixNioBasedEelFileSystemApi(fileSystem, EelTestPosixUserInfo(descriptor)) {

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> {
    TODO("Not yet implemented")
  }

  override suspend fun createTemporaryDirectory(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
    return wrapIntoEelResult {
      val nioTempDir = Files.createTempDirectory(fs.rootDirectories.single(), options.prefix)
      nioTempDir.asEelPath()
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