// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UpdateRequestParametersContributor {
  fun amendUpdateRequest(parameters: MutableMap<String, String>)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UpdateRequestParametersContributor> =
      ExtensionPointName("com.intellij.updateRequestParametersContributor")
  }
}
