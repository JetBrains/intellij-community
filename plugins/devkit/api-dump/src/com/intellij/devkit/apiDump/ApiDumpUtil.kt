// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.workspaceModel.ide.legacyBridge.findSnapshotModuleEntity
import kotlin.io.path.exists

object ApiDumpUtil {

  fun isApiDumpFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.nameMatches(ApiDumpConstants.API_DUMP_FILENAME)
  }

  fun isApiDumpExperimentalFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.nameMatches(ApiDumpConstants.API_DUMP_EXPERIMENTAL_FILENAME)
  }

  fun isApiDumpUnreviewedFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.nameMatches(ApiDumpConstants.API_DUMP_UNREVIEWED_FILENAME)
  }

  fun isExposedThirdPartyFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.EXPOSED_THIRD_PARTY_API_FILENAME
  }

  fun isExposedPrivateApiFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.name == ApiDumpConstants.EXPOSED_PRIVATE_API_FILENAME
  }

  fun Module.hasDump(): Boolean {
    val moduleEntity = findSnapshotModuleEntity()
    return moduleEntity != null && moduleEntity.hasDump()
  }

  fun ModuleEntity.hasDump(): Boolean {
    ThreadingAssertions.assertBackgroundThread()
    val firstContentRoot = contentRoots.firstOrNull() ?: return false
    val firstContentRootUrl = firstContentRoot.url
    return firstContentRootUrl.append(ApiDumpConstants.API_DUMP_FILENAME).exists() ||
           firstContentRootUrl.append(ApiDumpConstants.API_DUMP_UNREVIEWED_FILENAME).exists() ||
           firstContentRootUrl.append(ApiDumpConstants.API_DUMP_EXPERIMENTAL_FILENAME).exists()
  }

  private fun VirtualFileUrl.exists(): Boolean = toPath().exists()

  private fun VirtualFile.nameMatches(expectedName: String): Boolean {
    return this.name == expectedName ||
           isTestActualFile(expectedName, this.name)
  }

  /**
   * this function helps to recognize temp files used in ApiCheckTest: `actual_api-dump123.txt`
   */
  private fun isTestActualFile(constantName: String, actualName: String): Boolean {
    val extension = FileUtilRt.getExtension(constantName)
    val baseName = FileUtilRt.getNameWithoutExtension(constantName)

    return actualName.startsWith(ACTUAL_) &&
           actualName.endsWith(".$extension") &&
           actualName.regionMatches(ACTUAL_LENGTH, baseName, 0, baseName.length) &&
           actualName.substring(ACTUAL_LENGTH + baseName.length, actualName.length - extension.length - 1).let { it.isEmpty() || it.toIntOrNull() != null }
  }
}

private const val ACTUAL_ = "actual_"
private const val ACTUAL_LENGTH = ACTUAL_.length
