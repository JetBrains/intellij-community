package com.jetbrains.javascript.debugger

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.Url
import org.jetbrains.debugger.sourcemap.SourceFileResolver

abstract class FileUrlMapper {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<FileUrlMapper>("com.jetbrains.fileUrlMapper")
  }

  open fun getUrls(file: VirtualFile, project: Project, currentAuthority: String?): List<Url> = emptyList()

  /**
   * Optional to implement, useful if default navigation position to source file is not equals to 0:0 (java file for example)
   */
  open fun getNavigatable(url: Url, project: Project, requestor: Url?): Navigatable? = getFile(url, project, requestor)?.let { OpenFileDescriptor(project, it) }

  abstract fun getFile(url: Url, project: Project, requestor: Url?): VirtualFile?

  /**
   * Optional to implement, sometimes you cannot build URL, but can match.
   * Lifetime: resolve session lifetime. Could be called multiple times: n <= total sourcemap count
   */
  open fun createSourceResolver(file: VirtualFile, project: Project): SourceFileResolver? = null

  open fun getFileType(url: Url): FileType? = null
}
