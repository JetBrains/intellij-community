// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.NioFileChooserDialog
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Path

/**
 * Creates the dialogs of the universal file chooser.
 * The module `intellij.platform.ide.fileChooser.universal` registers the implementation as an application service.
 * Without the module, [getInstanceOrNull] returns `null`.
 */
interface UniversalFileChooserFactory {
  companion object {
    @JvmStatic
    fun getInstanceOrNull(): UniversalFileChooserFactory? =
      ApplicationManager.getApplication().getService(UniversalFileChooserFactory::class.java)
  }

  /**
   * Returns `true` if the universal file chooser replaces the default file chooser and file saver for [project].
   */
  fun canUseIn(project: Project?): Boolean

  fun createFileChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): FileChooserDialog

  fun createPathChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): PathChooserDialog

  /**
   * Creates a file chooser that returns the chosen files as [Path] values.
   *
   * When [project] is `null`, the chooser uses the default project.
   */
  @ApiStatus.Experimental
  fun createNioFileChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): NioFileChooserDialog

  /**
   * Creates a file chooser that shows only the roots of [contributors].
   *
   * [FileChooserDialog.choose] without a file to select navigates to the path from [preselectPath].
   * The chooser calls [preselectPath] on a background thread.
   * When [preselectPath] is null or gives null, the chooser navigates to the default path.
   */
  fun createFileChooser(
    project: Project,
    parent: Component?,
    descriptor: FileChooserDescriptor,
    contributors: Collection<UniversalFileChooserContributor>,
    preselectPath: (suspend () -> Path?)? = null,
  ): FileChooserDialog

  fun createFileSaver(project: Project?, parent: Component?, descriptor: FileSaverDescriptor): FileSaverDialog
}
