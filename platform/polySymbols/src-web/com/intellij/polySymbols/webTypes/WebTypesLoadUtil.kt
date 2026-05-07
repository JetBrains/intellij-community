// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.polySymbols.impl.objectMapper
import com.intellij.polySymbols.webTypes.json.WebTypes
import com.intellij.util.text.SemVer
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.CancellationException

@ApiStatus.Internal
fun InputStream.readWebTypes(): WebTypes =
  use { stream ->
    // EDT: CoroutineStart.UNDISPATCHED installs a Job (isInCancellableContext=true) but
    // runWithCheckCanceled forbids EDT — take the direct synchronous path instead.
    val app = ApplicationManager.getApplication()
    if (!isInCancellableContext() || app.isDispatchThread) {
      return objectMapper.readValue(stream, WebTypes::class.java)
    }
    // Not really practical to use DiskQueryRelay here, since streams are not realy reusable
    // Better to close the stream and cancel reading.
    runWithCheckCanceled {
      try {
        objectMapper.readValue(stream, WebTypes::class.java)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        if (!this.coroutineContext.job.isCancelled)
          throw e
        throw CancellationException("Reading web types from disk was cancelled")
      }
    }
  }

@ApiStatus.Internal
class WebTypesVersionsRegistry<T> {

  val packages: Set<String> get() = myVersions.keys
  val versions: Map<String, Map<SemVer, T>> get() = myVersions

  private val myVersions: SortedMap<String, SortedMap<SemVer, T>> = TreeMap()

  fun put(packageName: String, packageVersion: SemVer, value: T) {
    myVersions.computeIfAbsent(packageName) { TreeMap(Comparator.reverseOrder()) }[packageVersion] = value
  }

  fun get(packageName: String, packageVersion: SemVer?): T? =
    myVersions[packageName]?.let { get(it, packageVersion) }

  private fun get(
    versions: SortedMap<SemVer, T>?,
    pkgVersion: SemVer?,
  ): T? {
    if (versions.isNullOrEmpty()) {
      return null
    }
    var webTypesVersionEntry = (if (pkgVersion == null)
      versions.entries.find { it.key.preRelease == null }
      ?: versions.entries.firstOrNull()
    else
      versions.entries.find { it.key <= pkgVersion })
                               ?: return null

    if (webTypesVersionEntry.key.preRelease?.contains(LETTERS_PATTERN) == true) {
      // `2.0.0-beta.1` version is higher than `2.0.0-1`, so we need to manually find if there
      // is a non-alpha/beta/rc version available in such a case.
      versions.entries.find {
        it.key.major == webTypesVersionEntry.key.major
        && it.key.minor == webTypesVersionEntry.key.minor
        && it.key.patch == webTypesVersionEntry.key.patch
        && it.key.preRelease?.contains(NON_LETTERS_PATTERN) == true
      }
        ?.let { webTypesVersionEntry = it }
    }
    return webTypesVersionEntry.value
  }

  override fun equals(other: Any?): Boolean =
    other is WebTypesVersionsRegistry<*>
    && other.myVersions == myVersions

  override fun hashCode(): Int = myVersions.hashCode()

  companion object {
    private val LETTERS_PATTERN = Regex("[a-zA-Z]")
    private val NON_LETTERS_PATTERN = Regex("^[^a-zA-Z]+\$")
  }
}
