// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.Url
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UpdateRequestParametersContributor {
  fun amendUpdateRequest(parameters: MutableMap<String, String>)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UpdateRequestParametersContributor> =
      ExtensionPointName("com.intellij.updateRequestParametersContributor")

    fun passUpdateParameters(url: Url): Url {
      if (URLUtil.FILE_PROTOCOL == url.scheme) {
        return url
      }

      val parameters = LinkedHashMap<String, String>()
      // The default provider runs first; contributors run afterwards and may override the values it set
      DefaultUpdateRequestParametersProvider.amendUpdateRequest(parameters)
      EP_NAME.forEachExtensionSafe { it.amendUpdateRequest(parameters) }
      return url.addParameters(parameters)
    }
  }
}
