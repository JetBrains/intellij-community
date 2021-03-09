// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.dgm.DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE
import javax.swing.Icon

object DGMFileType : LanguageFileType(PropertiesLanguage.INSTANCE, true), FileTypeIdentifiableByVirtualFile {

  override fun getName(): String = "DGM"
  override fun getDefaultExtension(): String = ""
  override fun getDescription(): String = GroovyBundle.message("filetype.dgm.description")
  override fun getIcon(): Icon? = PropertiesFileType.INSTANCE.icon

  override fun isMyFileType(file: VirtualFile): Boolean {
    if (!Comparing.equal(ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE, file.nameSequence)) {
      return false
    }
    val parent = file.parent ?: return false
    val parentName = parent.nameSequence
    if (!Comparing.equal("services", parentName) && !Comparing.equal("groovy", parentName)) {
      return false
    }
    val gParent = parent.parent ?: return false
    return Comparing.equal("META-INF", gParent.nameSequence)
  }
}
