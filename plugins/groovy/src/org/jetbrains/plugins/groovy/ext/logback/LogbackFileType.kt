// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.Icon

object LogbackFileType : LanguageFileType(GroovyLanguage, true) {

  override fun getIcon(): Icon? = GroovyFileType.GROOVY_FILE_TYPE.icon
  override fun getName(): String = "logback"
  override fun getDescription(): String = GroovyBundle.message("filetype.logback.description")
  override fun getDefaultExtension(): String = "groovy"
}
