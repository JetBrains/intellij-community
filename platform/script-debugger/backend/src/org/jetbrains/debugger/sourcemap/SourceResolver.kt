/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

inline fun SourceResolver(rawSources: List<String>, sourceContents: List<String>?, urlCanonicalizer: (String) -> Url): SourceResolver {
  return SourceResolver(rawSources, Array(rawSources.size) { urlCanonicalizer(rawSources[it]) }, sourceContents)
}

fun SourceResolver(rawSources: List<String>,
                   trimFileScheme: Boolean,
                   baseUrl: Url?, sourceContents: List<String>?,
                   baseUrlIsFile: Boolean = true): SourceResolver {
  return SourceResolver(rawSources, sourceContents) { canonicalizeUrl(it, baseUrl, trimFileScheme, baseUrlIsFile) }
}

class SourceResolver(private val rawSources: List<String>, internal val canonicalizedUrls: Array<Url>, private val sourceContents: List<String>?) {
  private val canonicalizedUrlToSourceIndex: ObjectIntHashMap<Url> = if (SystemInfo.isFileSystemCaseSensitive) ObjectIntHashMap(rawSources.size) else ObjectIntHashMap(rawSources.size, Urls.getCaseInsensitiveUrlHashingStrategy())

  init {
    for (i in rawSources.indices) {
      canonicalizedUrlToSourceIndex.put(canonicalizedUrls[i], i)
    }
  }

  interface Resolver {
    fun resolve(sourceFile: VirtualFile?, map: ObjectIntHashMap<Url>): Int
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

  fun findMappings(sourceFile: VirtualFile?, sourceMap: SourceMap, resolver: Resolver): MappingList? {
    val index = resolver.resolve(sourceFile, canonicalizedUrlToSourceIndex)
    return if (index < 0) null else sourceMap.sourceIndexToMappings[index]
  }

  fun findMappings(sourceUrls: List<Url>, sourceMap: SourceMap, sourceFile: VirtualFile?): MappingList? {
    for (sourceUrl in sourceUrls) {
      val index = canonicalizedUrlToSourceIndex.get(sourceUrl)
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index]
      }
    }

    if (sourceFile != null) {
      findByFile(sourceMap, sourceFile)?.let {
        return it
      }
    }
    return null
  }

  fun findByFile(sourceMap: SourceMap, sourceFile: VirtualFile): MappingList? {
    var index = canonicalizedUrlToSourceIndex.get(Urls.newFromVirtualFile(sourceFile).trimParameters())
    if (index != -1) {
      return sourceMap.sourceIndexToMappings[index]
    }

    if (sourceFile.isInLocalFileSystem) {
      // local file url - without "file" scheme, just path
      index = canonicalizedUrlToSourceIndex.get(Urls.newLocalFileUrl(sourceFile))
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index]
      }
    }

    // ok, search by canonical path
    val canonicalFile = sourceFile.canonicalFile
    if (canonicalFile != null && canonicalFile != sourceFile) {
      for (i in canonicalizedUrls.indices) {
        val url = canonicalizedUrls[i]
        if (Urls.equalsIgnoreParameters(url, canonicalFile)) {
          return sourceMap.sourceIndexToMappings[i]
        }
      }
    }
    return null
  }

  fun getUrlIfLocalFile(entry: MappingEntry) = canonicalizedUrls.getOrNull(entry.source)?.let { if (it.isInLocalFileSystem) it else null }

  companion object {
    fun isAbsolute(path: String) = path.firstOrNull() == '/' || (SystemInfo.isWindows && (path.length > 2 && path[1] == ':'))
  }
}

fun canonicalizePath(url: String, baseUrl: Url, baseUrlIsFile: Boolean): String {
  var path = url
  if (url[0] != '/') {
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
  else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:")) {
    return Urls.parseEncoded(url) ?: UrlImpl(url)
  }
  else {
    return doCanonicalize(url, baseUrl, baseUrlIsFile, true)
  }
}

fun doCanonicalize(url: String, baseUrl: Url, baseUrlIsFile: Boolean, asLocalFileIfAbsoluteAndExists: Boolean): Url {
  val path = canonicalizePath(url, baseUrl, baseUrlIsFile)
  if ((baseUrl.scheme == null && baseUrl.isInLocalFileSystem) || (asLocalFileIfAbsoluteAndExists && SourceResolver.isAbsolute(path) && File(path).exists())) {
    return Urls.newLocalFileUrl(path)
  }
  else {
    return UrlImpl(baseUrl.scheme, baseUrl.authority, path, null)
  }
}