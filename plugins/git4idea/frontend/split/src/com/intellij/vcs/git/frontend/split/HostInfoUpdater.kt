// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.split

import com.intellij.openapi.client.ClientAppSession
import com.intellij.util.system.OS
import com.intellij.vcs.git.shared.CaseSensitivityInfoHolder
import com.jetbrains.rd.ide.model.HostInfoModel
import com.jetbrains.rd.protocol.RootExtListener
import com.jetbrains.rd.util.lifetime.Lifetime

internal class HostInfoUpdater : RootExtListener<HostInfoModel> {
  override fun extensionCreated(lifetime: Lifetime, session: ClientAppSession, model: HostInfoModel) {
    model.hostIdeInfo.advise(lifetime) { hostIdeInfo ->
      val os = OS.fromString(hostIdeInfo.osName)
      val caseSensitive = os != OS.Windows && os != OS.macOS
      CaseSensitivityInfoHolder.setCaseSensitivity(caseSensitive)
    }
  }
}