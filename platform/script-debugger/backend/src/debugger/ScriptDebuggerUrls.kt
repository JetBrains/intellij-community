// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ScriptDebuggerUrls {

  @JvmStatic
  fun newLocalFileUrl(path: String): Url {
    val uriPath = toUriPath(path)
    return Urls.newUrl(URLUtil.FILE_PROTOCOL, "", uriPath)
  }

  fun toFilePath(url: Url): String? {
    if (url.scheme != null && url.scheme != URLUtil.FILE_PROTOCOL) return null
    var path = url.path
    if (path.length >= 3 && path[0] == '/' && path[2] == ':') path = path.substring(1)
    return path
  }

  private fun toUriPath(path: String): String {
    var result = decodeAndConvertToSystemIndependent(path)
    if (result.length >= 2 && result[1] == ':') result = "/$result"
    return result
  }

  // Shumaf: I hate that we have so many ways to parse a URL, yet none of them are correct
  private fun decodeAndConvertToSystemIndependent(path: String): String = FileUtilRt.toSystemIndependentName(URLUtil.unescapePercentSequences(path))

  private fun toUri(absoluteOrRelativePath: String): Url {
    var result = decodeAndConvertToSystemIndependent(absoluteOrRelativePath)
    if (result.length >= 2 && result[1] == ':') result = "/$result"
    return if (result.isNotEmpty() && result[0] == '/') newLocalFileUrl(result) else Urls.newUnparsable(result)
  }

  @JvmStatic
  fun newLocalFileUrl(file: VirtualFile): Url = newLocalFileUrl(file.path)

  @JvmStatic
  fun parse(url: String, asLocalIfNoScheme: Boolean): Url? = when {
    asLocalIfNoScheme && !URLUtil.containsScheme(url) -> toUri(url)
    url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX) -> toLocalFileUrl(url)
    else -> Urls.parse(url, false)
  }

  fun toLocalFileUrl(url: String): Url {
    val canonicalPath = FileUtil.toCanonicalPath(VfsUtilCore.toIdeaUrl(url, true).substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length), '/')
    return newLocalFileUrl(canonicalPath)
  }
}