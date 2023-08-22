package com.intellij.remoteDev.tests.sm

import com.intellij.openapi.util.text.StringUtil

object SMTestFrameworkProtocolUtil {

  private const val protocolPrefix = "cwm://"

  fun encodeLocationUrl(locationUrl: String?): String? {
    locationUrl ?: return null
    return "$protocolPrefix$locationUrl"
  }

  fun decodeLocationUrl(locationUrl: String?): String? {
    locationUrl ?: return null
    return StringUtil.trimStart(locationUrl, protocolPrefix)
  }
}