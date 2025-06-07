// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.impl.fs.WindowsNioBasedEelFileSystemApi
import com.intellij.platform.eel.impl.local.EelLocalExecWindowsApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.toEelArch
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelUnitTestFileSystem
import com.intellij.util.system.CpuArch

internal class EelTestWindowsApi(override val descriptor: EelTestDescriptor, fileSystem: EelUnitTestFileSystem, localPrefix: String) : EelWindowsApi {
  override val userInfo: EelUserWindowsInfo = EelTestWindowsUserInfo(descriptor)

  override val fs: WindowsNioBasedEelFileSystemApi = EelTestFileSystemWindowsApi(descriptor, fileSystem)

  override val archive: EelArchiveApi
    get() = TODO()
  override val tunnels: EelTunnelsWindowsApi
    get() = TODO()
  override val exec: EelExecWindowsApi = EelLocalExecWindowsApi()

}

private class EelTestFileSystemWindowsApi(override val descriptor: EelDescriptor, fileSystem: EelUnitTestFileSystem) : WindowsNioBasedEelFileSystemApi(fileSystem, EelTestWindowsUserInfo(descriptor)) {

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> {
    TODO("Not yet implemented")
  }

  override suspend fun createTemporaryDirectory(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
    TODO("Not yet implemented")
  }

  override suspend fun createTemporaryFile(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> {
    TODO("Not yet implemented")
  }
}

private class EelTestWindowsUserInfo(descriptor: EelDescriptor) : EelUserWindowsInfo {
  override val home: EelPath = EelPath.parse("C:\\Users\\Test.User", descriptor)
}