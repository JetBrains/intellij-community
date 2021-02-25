// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.hosting

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import java.net.URI
import java.net.URISyntaxException

object GitHostingUrlUtil {
  @JvmStatic
  @NlsSafe
  fun removeProtocolPrefix(url: String): String {
    var index = url.indexOf('@')
    if (index != -1) {
      return url.substring(index + 1).replace(':', '/')
    }
    index = url.indexOf("://")
    return if (index != -1) {
      url.substring(index + 3)
    }
    else {
      url
    }
  }

  @JvmStatic
  fun getUriFromRemoteUrl(remoteUrl: String): URI? {
    val fixed = UriUtil.trimTrailingSlashes(remoteUrl).removeSuffix("/").removeSuffix(".git")
    return try {
      if (!fixed.contains(URLUtil.SCHEME_SEPARATOR)) {
        // scp-style
        URI(URLUtil.HTTPS_PROTOCOL + URLUtil.SCHEME_SEPARATOR + removeProtocolPrefix(fixed).replace(':', '/'))
      }
      else {
        URI(fixed)
      }
    }
    catch (e: URISyntaxException) {
      null
    }
  }
}