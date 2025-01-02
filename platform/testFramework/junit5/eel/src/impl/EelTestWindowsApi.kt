// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl

import com.intellij.platform.eel.EelArchiveApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPathMapper
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsWindowsApi
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.EelWindowsApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.impl.fs.WindowsNioBasedEelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.testFramework.junit5.eel.impl.nio.EelTestFileSystem
import com.intellij.util.system.CpuArch

internal class EelTestWindowsApi(fileSystem: EelTestFileSystem, localPrefix: String) : EelWindowsApi {
  override val userInfo: EelUserWindowsInfo = EelTestWindowsUserInfo()

  override val platform: EelPlatform.Windows
    get() = if (CpuArch.CURRENT == CpuArch.ARM64) {
      EelPlatform.Arm64Windows
    }
    else {
      EelPlatform.X64Windows
    }

  override val mapper: EelPathMapper = EelTestPathMapper(EelPath.OS.WINDOWS, fileSystem, localPrefix)

  override val fs: WindowsNioBasedEelFileSystemApi = EelTestFileSystemWindowsApi(fileSystem)

  override val archive: EelArchiveApi
    get() = TODO()
  override val tunnels: EelTunnelsWindowsApi
    get() = TODO()
  override val exec: EelExecApi
    get() = TODO()

}

private class EelTestFileSystemWindowsApi(fileSystem: EelTestFileSystem) : WindowsNioBasedEelFileSystemApi(fileSystem, EelTestWindowsUserInfo()) {

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

private class EelTestWindowsUserInfo : EelUserWindowsInfo {
  override val home: EelPath
    get() = EelPath.parse("/home", EelPath.OS.UNIX)
}