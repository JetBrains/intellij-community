// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author yole
 */
open class HashBangFileTypeDetector @JvmOverloads constructor(
  val fileType: FileType,
  val marker: String,
  val _version: Int = 1
) : FileTypeRegistry.FileTypeDetector {
  override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? {
    return if (FileUtil.isHashBangLine(firstCharsIfText, marker)) fileType else null
  }

  override fun getDesiredContentPrefixLength(): Int {
    // On vast majority of Linux systems, a restriction of 128 bytes of shebang length is compiled into kernel
    return 256
  }

  override fun getVersion() = _version

  override fun getDetectedFileTypes(): Collection<FileType> {
    return listOf(fileType)
  }
}
