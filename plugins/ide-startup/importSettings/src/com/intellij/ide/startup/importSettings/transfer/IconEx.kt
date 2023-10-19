// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.customize.transferSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.openapi.diagnostic.logger
import javax.swing.Icon

internal val TransferableIdeId.icon: Icon?
  get() = when (this) {
    TransferableIdeId.VSCode -> StartupImportIcons.Vscode.VSCode_48
    else -> {
      logger<TransferableIdeId>().error("Cannot find icon for transferable IDE $this.")
      null
    }
  }
