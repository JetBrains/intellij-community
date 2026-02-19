// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.PathUtilRt
import org.jetbrains.jps.util.JpsPathUtil

interface JpsExternalStorageMapping {
  fun getExternalSource(internalSource: JpsFileEntitySource): JpsFileEntitySource
  val externalStorageRoot: VirtualFileUrl
}

class JpsExternalStorageMappingImpl(override val externalStorageRoot: VirtualFileUrl,
                                    private val projectLocation: JpsProjectConfigLocation) : JpsExternalStorageMapping {
  override fun getExternalSource(internalSource: JpsFileEntitySource) = when (internalSource) {
    is JpsProjectFileEntitySource.FileInDirectory -> {
      val directoryPath = JpsPathUtil.urlToPath(internalSource.directory.url)
      val directoryName = PathUtilRt.getFileName(directoryPath)
      val parentPath = PathUtilRt.getParentPath(directoryPath)
      if (PathUtilRt.getFileName(parentPath) == ".idea" && (directoryName == "libraries" || directoryName == "artifacts")) {
        JpsProjectFileEntitySource.ExactFile(externalStorageRoot.append("project/$directoryName.xml"), projectLocation)
      }
      else {
        JpsProjectFileEntitySource.FileInDirectory(externalStorageRoot.append("modules"), projectLocation)
      }
    }
    else -> throw IllegalArgumentException("Unsupported internal entity source $internalSource")
  }
}
