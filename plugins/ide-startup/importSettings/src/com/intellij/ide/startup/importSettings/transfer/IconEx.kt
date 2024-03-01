// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.IconUtil
import javax.swing.Icon

internal fun TransferableIdeId.icon(size: IconProductSize): Icon? =
  when (this) {
    TransferableIdeId.VSCode -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.Vscode.VSCode_20
      IconProductSize.MIDDLE -> StartupImportIcons.Vscode.VSCode_24
      IconProductSize.LARGE -> StartupImportIcons.Vscode.VSCode_48
    }
    TransferableIdeId.VisualStudio -> when (size) {
      IconProductSize.SMALL -> StartupImportIcons.VisualStudio.VisualStudio_20
      IconProductSize.MIDDLE -> StartupImportIcons.VisualStudio.VisualStudio_24
      IconProductSize.LARGE -> StartupImportIcons.VisualStudio.VisualStudio_48
    }
    TransferableIdeId.VisualStudioForMac -> {
      val px = when (size) {
        IconProductSize.SMALL -> 20
        IconProductSize.MIDDLE -> 24
        IconProductSize.LARGE -> 48
      }
      val icon = AllIcons.TransferSettings.Vsmac
      val scale = px.toFloat() / icon.iconHeight
      IconUtil.scale(AllIcons.TransferSettings.Vsmac, null, scale)
    }
    else -> {
      logger<TransferableIdeId>().error("Cannot find icon for transferable IDE $this.")
      null
    }
  }
