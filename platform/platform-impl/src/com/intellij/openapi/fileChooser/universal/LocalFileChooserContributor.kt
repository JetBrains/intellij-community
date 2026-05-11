// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.actions.FileChooserActionUtil
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Companion.getFilteredSystemRoots
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import java.nio.file.Path

internal class LocalFileChooserContributor : UniversalFileChooserContributor {
  override val tabTitle: String = "Local"

  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = getFilteredSystemRoots { path -> ownsPath(path) }

  override fun ownsPath(path: Path): Boolean = path.asEelPath().descriptor is LocalEelDescriptor

  override fun getFileWatcherAdapter(): FileWatcherAdapter = LocalFileWatcherAdapter()

  override fun getDesktopPath(): Path? {
    return FileChooserActionUtil.getDesktopDir()
  }
}
