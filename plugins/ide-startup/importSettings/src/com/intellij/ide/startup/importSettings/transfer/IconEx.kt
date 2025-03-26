// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.openapi.diagnostic.logger
import javax.swing.Icon

internal fun TransferableIdeId.icon(size: IconProductSize): Icon? =
  when (this) {
    TransferableIdeId.VSCode -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.Vscode.VSCode_20
      IconProductSize.MIDDLE -> StartupImportIcons.Vscode.VSCode_24
      IconProductSize.LARGE -> StartupImportIcons.Vscode.VSCode_48
    }
    TransferableIdeId.VisualStudio, TransferableIdeId.VisualStudioForMac -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.VisualStudio.VisualStudio_20
      IconProductSize.MIDDLE -> StartupImportIcons.VisualStudio.VisualStudio_24
      IconProductSize.LARGE -> StartupImportIcons.VisualStudio.VisualStudio_48
    }
    TransferableIdeId.Cursor -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.Cursor.Cursor_20
      IconProductSize.MIDDLE -> StartupImportIcons.Cursor.Cursor_24
      IconProductSize.LARGE -> StartupImportIcons.Cursor.Cursor_48
    }
    TransferableIdeId.Windsurf -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.Windsurf.Windsurf_20
      IconProductSize.MIDDLE -> StartupImportIcons.Windsurf.Windsurf_24
      IconProductSize.LARGE -> StartupImportIcons.Windsurf.Windsurf_48
    }
    else -> {
      logger<TransferableIdeId>().error("Cannot find icon for transferable IDE $this.")
      null
    }
  }
