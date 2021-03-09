// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object GdslFileType : LanguageFileType(GroovyLanguage, true) {

  override fun getIcon(): Icon? = GroovyFileType.GROOVY_FILE_TYPE.icon
  override fun getName(): String = "gdsl"
  override fun getDescription(): String = GroovyBundle.message("filetype.gdsl.description")
  override fun getDefaultExtension(): String = "gdsl"
}
