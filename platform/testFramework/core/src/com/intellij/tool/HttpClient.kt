// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import com.intellij.TestCaseLoader
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

object HttpClient {
  private val locks = ConcurrentHashMap<String, Semaphore>()

  fun <Y> sendRequest(request: HttpUriRequest, processor: (HttpResponse) -> Y): Y {
    HttpClientBuilder.create()
      .setRedirectStrategy(LaxRedirectStrategy())
      .build().use { client ->
        client.execute(request).use { response ->
          if (response.statusLine.statusCode != 200) {
            System.err.println(
              "Response from ${request.uri} finished with status code ${response.statusLine.statusCode}. ${response.statusLine}")
          }
          return processor(response)
        }
      }
  }

  fun download(request: HttpUriRequest, outFile: File, retries: Long = 3): Boolean =
    download(request, outFile.toPath(), retries)

  fun download(request: HttpUriRequest, outStream: OutputStream, retries: Long = 3): Boolean {
    val tempFile = File.createTempFile("downloaded_", ".txt")

    val result = download(request, tempFile, retries)

    try {
      tempFile.bufferedReader().use { reader ->
        outStream.bufferedWriter().use { writer ->
          reader.lines().forEach { writer.write(it) }
        }
      }
      return result
    }
    catch (_: Throwable) {
      return false
    }
  }

  /**
   * Downloading file from [url] to [outPath] with [retries].
   * @return true - if successful, false - otherwise
   */
  fun download(request: HttpUriRequest, outPath: Path, retries: Long = 3): Boolean {
    val lock = locks.getOrPut(outPath.toAbsolutePath().toString()) { Semaphore(1) }
    lock.acquire()
    var isSuccessful = false

    return try {
      if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
        println("Downloading ${request.uri} to $outPath")
      }

      withRetry(retries = retries) {
        sendRequest(request) { response ->
          require(response.statusLine.statusCode == 200) { "Failed to download ${request.uri}: $response" }

          outPath.parent.createDirectories()
          outPath.outputStream().buffered(10 * 1024 * 1024).use { stream ->
            response.entity?.writeTo(stream)
          }

          isSuccessful = true
        }
      }

      isSuccessful
    }
    finally {
      if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
        println("Downloading ${request.uri} ${if (isSuccessful) "is successful" else "failed"}")
      }
      lock.release()
    }
  }
}