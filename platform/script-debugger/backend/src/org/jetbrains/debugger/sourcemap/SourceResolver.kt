/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy
import gnu.trove.TObjectIntHashMap
import org.jetbrains.io.LocalFileFinder

open class SourceResolver(private val rawSources: List<String>, trimFileScheme: Boolean, baseFileUrl: Url?, private val sourceContents: List<String>?, baseUrlIsFile: Boolean = true) {
  private val canonicalizedSourcesMap: ObjectIntHashMap<Url> = if (SystemInfo.isFileSystemCaseSensitive) ObjectIntHashMap(rawSources.size) else ObjectIntHashMap(rawSources.size, Urls.getCaseInsensitiveUrlHashingStrategy())

  internal val canonicalizedSources = Array(rawSources.size) { i ->
    val rawSource = rawSources[i]
    val url = canonicalizeUrl(rawSource, baseFileUrl, trimFileScheme, i, baseUrlIsFile)
    canonicalizedSourcesMap.put(url, i)
    url
  }

  private var absoluteLocalPathToSourceIndex: TObjectIntHashMap<String>? = null
  // absoluteLocalPathToSourceIndex contains canonical paths too, but this map contains only used (specified in the source map) path
  private var sourceIndexToAbsoluteLocalPath: Array<String?>? = null

  // see canonicalizeUri kotlin impl and https://trac.webkit.org/browser/trunk/Source/WebCore/inspector/front-end/ParsedURL.js completeURL
  protected open fun canonicalizeUrl(url: String, baseUrl: Url?, trimFileScheme: Boolean, sourceIndex: Int, baseUrlIsFile: Boolean): Url {
    if (trimFileScheme && url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
      return Urls.newLocalFileUrl(FileUtil.toCanonicalPath(VfsUtilCore.toIdeaUrl(url, true).substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length), '/'))
    }
    else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:")) {
      return Urls.parseEncoded(url) ?: UrlImpl(url)
    }

    val path = canonicalizePath(url, baseUrl, baseUrlIsFile)
    if (baseUrl.scheme == null && baseUrl.isInLocalFileSystem) {
      return Urls.newLocalFileUrl(path)
    }

    // browserify produces absolute path in the local filesystem
    if (isAbsolute(path)) {
      val file = LocalFileFinder.findFile(path)
      if (file != null) {
        if (absoluteLocalPathToSourceIndex == null) {
          // must be linked, on iterate original path must be first
          absoluteLocalPathToSourceIndex = createStringIntMap(rawSources.size)
          sourceIndexToAbsoluteLocalPath = arrayOfNulls<String>(rawSources.size)
        }
        absoluteLocalPathToSourceIndex!!.put(path, sourceIndex)
        sourceIndexToAbsoluteLocalPath!![sourceIndex] = path
        val canonicalPath = file.canonicalPath
        if (canonicalPath != null && canonicalPath != path) {
          absoluteLocalPathToSourceIndex!!.put(canonicalPath, sourceIndex)
        }
        return Urls.newLocalFileUrl(path)
      }
    }
    return UrlImpl(baseUrl.scheme, baseUrl.authority, path, null)
  }

  fun getSource(entry: MappingEntry): Url? {
    val index = entry.source
    return if (index < 0) null else canonicalizedSources[index]
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

  fun getSourceIndex(url: Url) = ArrayUtil.indexOf(canonicalizedSources, url)

  fun getRawSource(entry: MappingEntry): String? {
    val index = entry.source
    return if (index < 0) null else rawSources[index]
  }

  fun getLocalFilePath(entry: MappingEntry): String? {
    val index = entry.source
    return if (index < 0 || sourceIndexToAbsoluteLocalPath == null) null else sourceIndexToAbsoluteLocalPath!![index]
  }

  interface Resolver {
    fun resolve(sourceFile: VirtualFile?, map: ObjectIntHashMap<Url>): Int
  }

  fun findMappings(sourceFile: VirtualFile?, sourceMap: SourceMap, resolver: Resolver): MappingList? {
    val index = resolver.resolve(sourceFile, canonicalizedSourcesMap)
    return if (index < 0) null else sourceMap.sourceIndexToMappings[index]
  }

  fun findMappings(sourceUrls: List<Url>, sourceMap: SourceMap, sourceFile: VirtualFile?): MappingList? {
    for (sourceUrl in sourceUrls) {
      val index = canonicalizedSourcesMap.get(sourceUrl)
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index]
      }
    }

    if (sourceFile != null) {
      val mappings = findByFile(sourceMap, sourceFile)
      if (mappings != null) {
        return mappings
      }
    }
    return null
  }

  private fun findByFile(sourceMap: SourceMap, sourceFile: VirtualFile): MappingList? {
    var mappings: MappingList? = null
    if (absoluteLocalPathToSourceIndex != null && sourceFile.isInLocalFileSystem) {
      mappings = getMappingsBySource(sourceMap, absoluteLocalPathToSourceIndex!!.get(sourceFile.path))
      if (mappings == null) {
        val sourceFileCanonicalPath = sourceFile.canonicalPath
        if (sourceFileCanonicalPath != null) {
          mappings = getMappingsBySource(sourceMap, absoluteLocalPathToSourceIndex!!.get(sourceFileCanonicalPath))
        }
      }
    }

    if (mappings == null) {
      val index = canonicalizedSourcesMap.get(Urls.newFromVirtualFile(sourceFile).trimParameters())
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index]
      }

      for (i in canonicalizedSources.indices) {
        val url = canonicalizedSources[i]
        if (Urls.equalsIgnoreParameters(url, sourceFile)) {
          return sourceMap.sourceIndexToMappings[i]
        }

        val canonicalFile = sourceFile.canonicalFile
        if (canonicalFile != null && canonicalFile != sourceFile && Urls.equalsIgnoreParameters(url, canonicalFile)) {
          return sourceMap.sourceIndexToMappings[i]
        }
      }
    }
    return mappings
  }

  companion object {
    fun isAbsolute(path: String): Boolean {
      return !path.isEmpty() && (path[0] == '/' || (SystemInfo.isWindows && (path.length > 2 && path[1] == ':')))
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
      path = FileUtil.toCanonicalPath(path, '/')
      return path
    }

    private fun getMappingsBySource(sourceMap: SourceMap, index: Int) = if (index == -1) null else sourceMap.sourceIndexToMappings[index]
  }
}

private fun createStringIntMap(initialCapacity: Int) = if (SystemInfo.isFileSystemCaseSensitive) ObjectIntHashMap<String>(initialCapacity) else ObjectIntHashMap(initialCapacity, CaseInsensitiveStringHashingStrategy.INSTANCE)