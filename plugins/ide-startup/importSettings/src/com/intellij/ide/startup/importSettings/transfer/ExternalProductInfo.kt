// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.chooser.ui.SettingsImportOrigin
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import java.time.LocalDate


data class ExternalProductInfo(
  val transferableId: TransferableIdeId,
  override val id: String,
  override val name: String,
  override val version: String,
  override val lastUsage: LocalDate,
  val comment: String
) : Product {
  override val origin = SettingsImportOrigin.ThirdPartyProduct

  companion object {

    fun ofIdeVersion(ideVersion: IdeVersion) = ExternalProductInfo(
      ideVersion.transferableId,
      ideVersion.id,
      ideVersion.name,
      version = "",
      lastUsage = LocalDate.now(),
      comment = ideVersion.subName ?: ""
    )
  }
}
