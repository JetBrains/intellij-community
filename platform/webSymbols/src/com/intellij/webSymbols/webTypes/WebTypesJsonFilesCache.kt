// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.util.io.HttpRequests
import com.intellij.webSymbols.webTypes.json.WebTypes
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.ApiStatus
import java.io.*
import java.net.URI

@ApiStatus.Internal
object WebTypesJsonFilesCache {
  private const val WEB_TYPES_FILE_SUFFIX = ".web-types.json"

  fun fromUrlNoCache(url: String): WebTypes =
    getWebTypesJson(url).readWebTypes()

  fun getWebTypesJson(url: String): InputStream {
    if (url.endsWith("json") && url.startsWith(StandardFileSystems.FILE_PROTOCOL + ":")) {
      return FileInputStream(File(URI(url)))
    }
    val downloadedJson = File(PathManager.getSystemPath(),
                              "web-types/" + File(url).nameWithoutExtension + WEB_TYPES_FILE_SUFFIX)
    if (!downloadedJson.exists()) {
      downloadedJson.parentFile.mkdirs()
      val content = downloadWebTypesJson(url)
      val mapper = ObjectMapper()
      val webTypesJson = mapper.readTree(content)
      mapper.writer().writeValue(downloadedJson, webTypesJson)
    }
    return FileInputStream(downloadedJson)
  }

  private fun downloadWebTypesJson(tarball: String): String? {
    val contents = HttpRequests.request(tarball).readBytes(null)
    val bi = BufferedInputStream(ByteArrayInputStream(contents))
    val gzi = GzipCompressorInputStream(bi)
    val input = TarArchiveInputStream(gzi)
    var e: ArchiveEntry? = input.nextEntry
    while (e != null) {
      if (e.name.endsWith(WEB_TYPES_FILE_SUFFIX)) {
        if (input.canReadEntryData(e)) {
          return FileUtil.loadTextAndClose(input)
        }
      }
      e = input.nextEntry
    }
    return null
  }
}
