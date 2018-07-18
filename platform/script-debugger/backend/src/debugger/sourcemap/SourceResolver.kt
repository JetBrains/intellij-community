// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.containers.ObjectIntHashMap
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.io.URLUtil
import java.io.File

interface SourceFileResolver {
  /**
   * Return -1 if no match
   */
  fun resolve(map: ObjectIntHashMap<Url>): Int = -1
  fun resolve(rawSources: List<String>): Int = -1
}

class SourceResolver(private val rawSources: List<String>,
                     trimFileScheme: Boolean,
                     baseUrl: Url?,
                     private val sourceContents: List<String?>?,
                     baseUrlIsFile: Boolean = true) {
  companion object {
    fun isAbsolute(path: String): Boolean = path.startsWith('/') || (SystemInfo.isWindows && (path.length > 2 && path[1] == ':'))
  }

  val canonicalizedUrls: Array<Url> by lazy {
    Array(rawSources.size) { canonicalizeUrl(rawSources[it], baseUrl, trimFileScheme, baseUrlIsFile) }
  }

  private val canonicalizedUrlToSourceIndex: ObjectIntHashMap<Url> by lazy {
    (
      if (SystemInfo.isFileSystemCaseSensitive) ObjectIntHashMap(rawSources.size)
      else ObjectIntHashMap(rawSources.size, Urls.caseInsensitiveUrlHashingStrategy)
    ).also {
      for (i in rawSources.indices) {
        it.put(canonicalizedUrls[i], i)
      }
    }
  }

  fun getSource(entry: MappingEntry): Url? {
    val index = entry.source
    return if (index < 0) null else canonicalizedUrls[index]
  }

  fun getSourceContent(entry: MappingEntry): String? {
    if (sourceContents.isNullOrEmpty()) {
      return null
    }

    val index = entry.source
    return if (index < 0 || index >= sourceContents!!.size) null else sourceContents[index]
  }

  fun getSourceContent(sourceIndex: Int): String? {
    if (sourceContents.isNullOrEmpty()) {
      return null
    }
    return if (sourceIndex < 0 || sourceIndex >= sourceContents!!.size) null else sourceContents[sourceIndex]
  }

  fun getSourceIndex(url: Url): Int = canonicalizedUrlToSourceIndex[url]

  fun getRawSource(entry: MappingEntry): String? {
    val index = entry.source
    return if (index < 0) null else rawSources[index]
  }

  internal fun findSourceIndex(resolver: SourceFileResolver): Int {
    val resolveByCanonicalizedUrls = resolver.resolve(canonicalizedUrlToSourceIndex)
    return if (resolveByCanonicalizedUrls != -1) resolveByCanonicalizedUrls else resolver.resolve(rawSources)
  }

  fun findSourceIndex(sourceUrls: List<Url>, sourceFile: VirtualFile?, localFileUrlOnly: Boolean): Int {
    for (sourceUrl in sourceUrls) {
      val index = canonicalizedUrlToSourceIndex.get(sourceUrl)
      if (index != -1) {
        return index
      }
    }

    if (sourceFile != null) {
      return findSourceIndexByFile(sourceFile, localFileUrlOnly)
    }
    return -1
  }

  internal fun findSourceIndexByFile(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int {
    if (!localFileUrlOnly) {
      val index = canonicalizedUrlToSourceIndex.get(Urls.newFromVirtualFile(sourceFile).trimParameters())
      if (index != -1) {
        return index
      }
    }

    if (!sourceFile.isInLocalFileSystem) {
      return -1
    }

    // local file url - without "file" scheme, just path
    val index = canonicalizedUrlToSourceIndex.get(Urls.newLocalFileUrl(sourceFile))
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

fun canonicalizePath(url: String, baseUrl: Url, baseUrlIsFile: Boolean): String {
  var path = url
  if (!FileUtil.isAbsolute(url) && !url.isEmpty() && url[0] != '/') {
    val basePath = baseUrl.path
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
    }
    else {
      path = "$basePath/$url"
    }
  }
  return FileUtil.toCanonicalPath(path, '/')
}

// see canonicalizeUri kotlin impl and https://trac.webkit.org/browser/trunk/Source/WebCore/inspector/front-end/ParsedURL.js completeURL
fun canonicalizeUrl(url: String, baseUrl: Url?, trimFileScheme: Boolean, baseUrlIsFile: Boolean = true): Url {
  if (trimFileScheme && url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
    return Urls.newLocalFileUrl(FileUtil.toCanonicalPath(VfsUtilCore.toIdeaUrl(url, true).substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length), '/'))
  }
  else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") ||
           url.startsWith("javascript:") || url.startsWith("webpack:")) {
    // consider checking :/ instead of :// because scheme may be followed by path, not by authority
    // https://tools.ietf.org/html/rfc3986#section-1.1.2
    // be careful with windows paths: C:/Users
    return Urls.parseEncoded(url) ?: Urls.newUri(null, url)
  }
  else {
    return doCanonicalize(url, baseUrl, baseUrlIsFile, true)
  }
}

fun doCanonicalize(url: String, baseUrl: Url, baseUrlIsFile: Boolean, asLocalFileIfAbsoluteAndExists: Boolean): Url {
  val path = canonicalizePath(url, baseUrl, baseUrlIsFile)
  if ((baseUrl.scheme == null && baseUrl.isInLocalFileSystem) ||
      asLocalFileIfAbsoluteAndExists && SourceResolver.isAbsolute(path) && File(path).exists()) {
    // file:///home/user/foo.js.map, foo.ts -> /home/user/foo.ts (baseUrl is in local fs)
    // http://localhost/home/user/foo.js.map, foo.ts -> /home/user/foo.ts (File(path) exists)
    return Urls.newLocalFileUrl(path)
  }
  else {
    // new url from path and baseUrl's scheme and authority
    val split = path.split('?', limit = 2)
    return Urls.newUrl(baseUrl.scheme!!, baseUrl.authority!!, split[0], if (split.size > 1) '?' + split[1] else null)
  }
}