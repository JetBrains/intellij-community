// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.GroovyEnabledFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.Icon

object GradleFileType : LanguageFileType(GroovyLanguage, true), GroovyEnabledFileType {
  override fun getIcon(): Icon = icons.GradleIcons.GradleFile
  override fun getName(): String = "Gradle"
  override fun getDescription(): String = GradleBundle.message("gradle.filetype.description")
  override fun getDisplayName(): String = GradleBundle.message("gradle.filetype.display.name")
  override fun getDefaultExtension(): String = GradleConstants.EXTENSION

  @JvmStatic
  fun isGradleFile(file: VirtualFile) = FileTypeRegistry.getInstance().isFileOfType(file, GradleFileType)

  @JvmStatic
  fun isGradleFile(file: PsiFile): Boolean {
    val virtualFile = file.originalFile.virtualFile ?: return false
    return isGradleFile(virtualFile)
  }
}

fun VirtualFile?.isGradleFile() = this != null && GradleFileType.isGradleFile(this)
fun PsiFile?.isGradleFile() = this != null && GradleFileType.isGradleFile(this)
