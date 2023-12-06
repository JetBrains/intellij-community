// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import git4idea.remote.GitRemoteUrlCoordinates
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException

object GitHostingUrlUtil {
  @JvmStatic
  @NlsSafe
  fun removeProtocolPrefix(url: String): String {
    var index = url.indexOf('@')
    if (index != -1) {
      return url.substring(index + 1)
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
        URI(URLUtil.HTTPS_PROTOCOL + URLUtil.SCHEME_SEPARATOR + removeProtocolPrefix(fixed).replace(":/", "/").replace(':', '/'))
      }
      else {
        URI(fixed)
      }
    }
    catch (e: URISyntaxException) {
      null
    }
  }

  @JvmStatic
  fun match(serverUri: URI, gitRemoteUrl: String): Boolean {
    val remoteUri = getUriFromRemoteUrl(gitRemoteUrl) ?: return false

    if (!serverUri.host.equals(remoteUri.host, true)) return false

    if (serverUri.path != null && serverUri.path != "/") {
      val remoteUriPath = remoteUri.path ?: return false
      if (!remoteUriPath.startsWith(serverUri.path, true)) return false
    }
    return true
  }

  @ApiStatus.Internal
  suspend fun <S : ServerPath> findServerAt(log: Logger, coordinates: GitRemoteUrlCoordinates, serverCheck: suspend (URI) -> S?): S? {
    val uri = getUriFromRemoteUrl(coordinates.url)
    log.debug("Extracted URI $uri from remote ${coordinates.url}")
    if (uri == null) return null

    val host = uri.host ?: return null
    val path = uri.path ?: return null
    val pathParts = path.removePrefix("/").split('/').takeIf { it.size >= 2 } ?: return null
    val serverSuffix = if (pathParts.size == 2) null else pathParts.subList(0, pathParts.size - 2).joinToString("/")

    for (serverUri in listOf(
      URI(URLUtil.HTTPS_PROTOCOL, host, serverSuffix, null),
      URI(URLUtil.HTTP_PROTOCOL, host, serverSuffix, null),
      URI(URLUtil.HTTP_PROTOCOL, null, host, 8080, serverSuffix, null, null)
    )) {
      log.debug("Looking for server at $serverUri")
      try {
        val server = serverCheck(serverUri)
        if (server != null) {
          log.debug("Found server at $serverUri")
          return server
        }
      }
      catch (ignored: Throwable) {
      }
    }
    return null
  }
}