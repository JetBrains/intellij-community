// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.append
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation

interface JpsExternalStorageMapping {
  fun getExternalSource(internalSource: JpsFileEntitySource): JpsFileEntitySource
  val externalStorageRoot: VirtualFileUrl
}

class JpsExternalStorageMappingImpl(override val externalStorageRoot: VirtualFileUrl, private val projectLocation: JpsProjectConfigLocation) : JpsExternalStorageMapping {
  override fun getExternalSource(internalSource: JpsFileEntitySource) = when (internalSource) {
    is JpsFileEntitySource.FileInDirectory -> {
      val directoryPath = VfsUtil.urlToPath(internalSource.directory.url)
      val directoryName = PathUtil.getFileName(directoryPath)
      val parentPath = PathUtil.getParentPath(directoryPath)
      if (PathUtil.getFileName(parentPath) == ".idea" && (directoryName == "libraries" || directoryName == "artifacts")) {
        JpsFileEntitySource.ExactFile(externalStorageRoot.append("project/$directoryName.xml"), projectLocation)
      }
      else {
        JpsFileEntitySource.FileInDirectory(externalStorageRoot.append("modules"), projectLocation)
      }
    }
    else -> throw IllegalArgumentException("Unsupported internal entity source $internalSource")
  }
}
