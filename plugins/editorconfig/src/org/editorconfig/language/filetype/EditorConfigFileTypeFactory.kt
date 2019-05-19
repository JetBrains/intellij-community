// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.filetype

import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory

class EditorConfigFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) =
    consumer.consume(EditorConfigFileType, ExactFileNameMatcher(EditorConfigFileConstants.FILE_NAME))
}
