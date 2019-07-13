// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ignore

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import org.zmlx.hg4idea.ignore.lang.HgIgnoreLanguage

class HgIgnoreFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) =
    HgIgnoreLanguage.INSTANCE.fileType.let { fileType-> consumer.consume(fileType, fileType.ignoreLanguage.extension) }
}