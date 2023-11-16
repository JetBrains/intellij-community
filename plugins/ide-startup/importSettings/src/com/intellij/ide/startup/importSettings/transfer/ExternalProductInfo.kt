// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import java.time.LocalDate


data class ExternalProductInfo(
  override val id: String,
  override val name: String,
  override val version: String,
  override val lastUsage: LocalDate
) : Product {

  companion object {

    fun ofIdeVersion(ideVersion: BaseIdeVersion) = ExternalProductInfo(
      ideVersion.id,
      ideVersion.name,
      ideVersion.subName ?: "",
      // TODO: Calculate the last access date properly
      LocalDate.now()
    )
  }
}