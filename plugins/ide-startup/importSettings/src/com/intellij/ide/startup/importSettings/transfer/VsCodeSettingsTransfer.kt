// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.providers.cursor.CursorTransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.providers.windsurf.WindsurfTransferSettingsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope

class VsCodeSettingsTransfer : ThirdPartyProductSettingsTransfer {

  override fun getProviders(): List<VSCodeTransferSettingsProvider> {
    val scope = service<ScopeHolder>().scope
    return listOf(VSCodeTransferSettingsProvider(scope), CursorTransferSettingsProvider(scope), WindsurfTransferSettingsProvider(scope))
  }
}

@Service
private class ScopeHolder(val scope: CoroutineScope)
