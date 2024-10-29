// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.apiDump

import com.intellij.openapi.vfs.VirtualFile

internal object ApiDumpUtil {

  fun isApiDumpFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.API_DUMP_FILENAME
  }

  fun isApiDumpExperimentalFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.API_DUMP_EXPERIMENTAL_FILENAME
  }

  fun isApiDumpUnreviewedFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.API_DUMP_UNREVIEWED_FILENAME
  }

  fun isExposedThirdPartyFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.EXPOSED_THIRD_PARTY_API_FILENAME
  }

  fun isExposedPrivateApiFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.EXPOSED_PRIVATE_API_FILENAME
  }

}