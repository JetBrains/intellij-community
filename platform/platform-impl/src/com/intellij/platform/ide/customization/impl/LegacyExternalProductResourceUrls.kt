// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.customization.impl

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.platform.ide.customization.ExternalProductResourceUrls

class LegacyExternalProductResourceUrls : ExternalProductResourceUrls {
  override val updatesMetadataXmlUrl: String?
    get() = ApplicationInfoEx.getInstanceEx().updateUrls?.checkingUrl
  override val basePatchDownloadUrl: String?
    get() = ApplicationInfoEx.getInstanceEx().updateUrls?.patchesUrl
}