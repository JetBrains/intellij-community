// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.testProvider

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferableIdeFeatureId
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.models.PluginFeature
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import java.util.*

class TestTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId = TransferableIdeId.DummyIde
  override val name: String = "Test"

  override fun isAvailable(): Boolean = true

  val saved = listOf(IdeVersion(transferableIdeId, null, "test23", AllIcons.CodeWithMe.CwmJoin, "Test Instance", "yes", {
    Settings(

      laf = KnownLafs.Light,
      //keymap = BundledKeymap("My cool keymap", "Sublime Text", emptyList(/* fill this with shortcuts samples or action ids */)),
      plugins = mutableMapOf(
        "dummy.plugin" to PluginFeature(TransferableIdeFeatureId.DummyPlugin, "com.intellij.ideolog", "Ideolog")
      )
    )
  }, Date(), this))

  override fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion> {
    if (skipIds.isNotEmpty()) return emptyList()
    return saved
  }
}