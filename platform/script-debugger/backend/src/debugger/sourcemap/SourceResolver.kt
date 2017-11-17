/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.Url
import com.intellij.util.UrlImpl
import com.intellij.util.Urls
import com.intellij.util.containers.ObjectIntHashMap
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.io.URLUtil
import java.io.File

inline fun SourceResolver(rawSources: List<String>, sourceContents: List<String?>?, urlCanonicalizer: (String) -> Url): SourceResolver {
  return SourceResolver(rawSources, Array(rawSources.size) { urlCanonicalizer(rawSources[it]) }, sourceContents)
}

fun SourceResolver(rawSources: List<String>,
                   trimFileScheme: Boolean,
                   baseUrl: Url?,
                   sourceContents: List<String?>?,
                   baseUrlIsFile: Boolean = true): SourceResolver {
  return SourceResolver(rawSources, sourceContents) { canonicalizeUrl(it, baseUrl, trimFileScheme, baseUrlIsFile) }
}

interface SourceFileResolver {
  /**
   * Return -1 if no match
   */
  fun resolve(map: ObjectIntHashMap<Url>): Int = -1
  fun resolve(rawSources: List<String>): Int = -1
}

class SourceResolver(private val rawSources: List<String>, val canonicalizedUrls: Array<Url>, private val sourceContents: List<String?>?) {
  companion object {
    fun isAbsolute(path: String) = path.startsWith('/') || (SystemInfo.isWindows && (path.length > 2 && path[1] == ':'))
  }

  private val canonicalizedUrlToSourceIndex: ObjectIntHashMap<Url> = if (SystemInfo.isFileSystemCaseSensitive) ObjectIntHashMap(rawSources.size) else ObjectIntHashMap(rawSources.size, Urls.getCaseInsensitiveUrlHashingStrategy())

  init {
    for (i in rawSources.indices) {
      canonicalizedUrlToSourceIndex.put(canonicalizedUrls[i], i)
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

  fun getSourceIndex(url: Url) = ArrayUtil.indexOf(canonicalizedUrls, url)

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

  fun getUrlIfLocalFile(entry: MappingEntry) = canonicalizedUrls.getOrNull(entry.source)?.let { if (it.isInLocalFileSystem) it else null }
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
    return Urls.parseEncoded(url) ?: UrlImpl(url)
  }
  else {
    return doCanonicalize(url, baseUrl, baseUrlIsFile, true)
  }
}

fun doCanonicalize(url: String, baseUrl: Url, baseUrlIsFile: Boolean, asLocalFileIfAbsoluteAndExists: Boolean): Url {
  val path = canonicalizePath(url, baseUrl, baseUrlIsFile)
  if (baseUrl.scheme == null && baseUrl.isInLocalFileSystem) {
    return Urls.newLocalFileUrl(path)
  }
  else if (asLocalFileIfAbsoluteAndExists && SourceResolver.isAbsolute(path)) {
    return if (File(path).exists()) Urls.newLocalFileUrl(path) else Urls.parse(url, false) ?: UrlImpl(null, null, url, null)
  }
  else {
    val split = path.split('?', limit = 2)
    return UrlImpl(baseUrl.scheme, baseUrl.authority, split[0], if (split.size > 1) '?' + split[1] else null)
  }
}