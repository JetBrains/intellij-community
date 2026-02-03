// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.UriUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.URLUtil
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.debugger.ScriptDebuggerUrls
import java.nio.file.InvalidPathException
import kotlin.io.path.Path
import kotlin.io.path.exists

private val LOG = logger<SourceResolver>()

@ApiStatus.Internal
interface SourceFileResolver {
  /**
   * Return -1 if no match
   */
  fun resolve(map: Object2IntMap<Url>): Int = -1
  fun resolve(rawSources: List<String>): Int = -1
}

@ApiStatus.Internal
class SourceResolver(
  private val rawSources: List<String>,
  baseUrl: Url?,
  baseUrlIsFile: Boolean = true,
  transformToLocalFileUrlIfPossible: Boolean = true,
) : SourceFileResolver {
  companion object {
    fun isAbsolute(path: String): Boolean = path.startsWith('/') || (SystemInfo.isWindows && (path.length > 2 && path[1] == ':'))
  }

  val canonicalizedUrls: Array<Url> by lazy {
    Array(rawSources.size) { canonicalizeUrl(rawSources[it], baseUrl, baseUrlIsFile, transformToLocalFileUrlIfPossible) }
  }

  private val canonicalizedUrlToSourceIndex: Object2IntMap<Url> by lazy {
    val map: Object2IntMap<Url> = if (SystemInfo.isFileSystemCaseSensitive) {
      Object2IntOpenHashMap(rawSources.size)
    }
    else {
      Object2IntOpenCustomHashMap(rawSources.size, CaseInsensitiveUrlHashingStrategy)
    }
    map.defaultReturnValue(-1)

    for (i in rawSources.indices) {
      map.put(canonicalizedUrls[i], i)
    }
    map
  }

  fun getSource(entry: MappingEntry): Url? {
    val index = entry.source
    return if (index < 0) null else canonicalizedUrls[index]
  }

  fun getSourceIndex(url: Url): Int = canonicalizedUrlToSourceIndex.getInt(url)

  internal fun findSourceIndex(resolver: SourceFileResolver): Int {
    val resolveByCanonicalizedUrls = resolver.resolve(canonicalizedUrlToSourceIndex)
    return if (resolveByCanonicalizedUrls != -1) resolveByCanonicalizedUrls else resolver.resolve(rawSources)
  }

  fun findSourceIndex(sourceUrl: Url, sourceFile: VirtualFile?, localFileUrlOnly: Boolean): Int {
    val index = canonicalizedUrlToSourceIndex.getInt(sourceUrl)
    if (index != -1) {
      return index
    }

    if (sourceFile != null) {
      return findSourceIndexByFile(sourceFile, localFileUrlOnly)
    }
    return -1
  }

  internal fun findSourceIndexByFile(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int {
    if (!localFileUrlOnly) {
      val index = canonicalizedUrlToSourceIndex.getInt(Urls.newFromVirtualFile(sourceFile).trimParameters())
      if (index != -1) {
        return index
      }
    }

    if (!sourceFile.isInLocalFileSystem) {
      return -1
    }

    val index = canonicalizedUrlToSourceIndex.getInt(ScriptDebuggerUrls.newLocalFileUrl(sourceFile))
    if (index != -1) {
      return index
    }

    // ok, search by canonical path
    val canonicalFile = sourceFile.canonicalFile
    if (canonicalFile != null && canonicalFile != sourceFile) {
      for (i in canonicalizedUrls.indices) {
        val url = canonicalizedUrls.get(i)
        if (Urls.equalsIgnoreParameters(url, canonicalFile)) {
          return i
        }
      }
    }
    return -1
  }

  fun getUrlIfLocalFile(entry: MappingEntry): Url? = canonicalizedUrls.getOrNull(entry.source)?.let { if (it.isInLocalFileSystem) it else null }
}

@ApiStatus.Internal
fun canonicalizePath(url: String, baseUrl: Url, baseUrlIsFile: Boolean): String {
  var path = UriUtil.trimParameters(url)
  if (!FileUtil.isAbsolute(url) && url.isNotEmpty() && url[0] != '/') {
    val basePath = ScriptDebuggerUrls.toFilePath(baseUrl) ?: baseUrl.path
    if (baseUrlIsFile) {
      val lastSlashIndex = basePath.lastIndexOf('/')
      val pathBuilder = StringBuilder()
      if (lastSlashIndex == -1) {
        pathBuilder.append('/')
      }
      else {
        pathBuilder.append(basePath, 0, lastSlashIndex + 1)
      }
      path = pathBuilder.append(url).toString()
      // URL path part isn't always a valid path in some OS (i.e. contains characters that are prohibited to use in path on Windows).
      // So to resolve symlinks we need to access to FS API, but we can't do that if path is invalid for some reason.
      val resolveSymlinks = isValidPath(path)
      return FileUtil.toCanonicalPath(path, resolveSymlinks)
    }
    else {
      path = "$basePath/$url"
    }
  }
  return FileUtil.toCanonicalPath(path, '/')
}

private fun isValidPath(path: String): Boolean {
  return try {
    Path(path)
    true
  }
  catch (_: InvalidPathException) {
    false
  }
}

// see canonicalizeUri kotlin impl and https://trac.webkit.org/browser/trunk/Source/WebCore/inspector/front-end/ParsedURL.js completeURL
@ApiStatus.Internal
fun canonicalizeUrl(url: String, baseUrl: Url?, baseUrlIsFile: Boolean = true, transformToLocalFileUrlIfPossible: Boolean = true): Url {
  if (url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
    return ScriptDebuggerUrls.toLocalFileUrl(url)
  }
  else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") ||
           url.startsWith("javascript:") || url.startsWith("webpack:")) {
    // consider checking :/ instead of :// because scheme may be followed by path, not by authority
    // https://tools.ietf.org/html/rfc3986#section-1.1.2
    // be careful with windows paths: C:/Users
    return Urls.parseEncoded(url) ?: Urls.newUri(null, url)
  }
  else {
    return doCanonicalize(url, baseUrl, baseUrlIsFile, transformToLocalFileUrlIfPossible)
  }
}

@ApiStatus.Internal
fun doCanonicalize(url: String, baseUrl: Url, baseUrlIsFile: Boolean, asLocalFileIfAbsoluteAndExists: Boolean): Url {
  val path = canonicalizePath(url, baseUrl, baseUrlIsFile)
  if (baseUrl.isInLocalFileSystem || asLocalFileIfAbsoluteAndExists && SourceResolver.isAbsolute(path) && pathExistsSafe(path)) {
    // file:///home/user/foo.js.map, foo.ts -> file:///home/user/foo.ts (baseUrl is in local fs)
    // http://localhost/home/user/foo.js.map, foo.ts -> file:///home/user/foo.ts (File(path) exists)
    return ScriptDebuggerUrls.newLocalFileUrl(path)
  }
  else if (!path.startsWith("/")) {
    // http://localhost/source.js.map, C:/foo.ts webpack-dsj3c45 -> C:/foo.ts webpack-dsj3c45
    // (we can't append path suffixes unless they start with /
    return ScriptDebuggerUrls.parse(path, true) ?: Urls.newUnparsable(path)
  }
  else {
    // new url from path and baseUrl's scheme and authority
    val split = path.split('?', limit = 2)
    return Urls.newUrl(baseUrl.scheme, baseUrl.authority, split[0], if (split.size > 1) '?' + split[1] else null)
  }
}

private fun pathExistsSafe(path: String): Boolean = try {
  Path(path).exists()
} catch (e: InvalidPathException) {
  LOG.warn("Invalid path: $path", e)
  false
}

private object CaseInsensitiveUrlHashingStrategy: Hash.Strategy<Url> {
  override fun hashCode(url: Url?) = url?.hashCodeCaseInsensitive() ?: 0

  override fun equals(url1: Url?, url2: Url?) = Urls.equals(url1, url2, caseSensitive = false, ignoreParameters = false)
}