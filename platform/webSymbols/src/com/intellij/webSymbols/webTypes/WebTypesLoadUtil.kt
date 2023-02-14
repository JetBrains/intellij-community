// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StringDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.util.containers.Interner
import com.intellij.util.text.SemVer
import com.intellij.webSymbols.webTypes.json.WebTypes
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.util.*

private val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .setTypeFactory(TypeFactory.defaultInstance().withClassLoader(WebTypes::class.java.classLoader))
  .registerModule(SimpleModule().also { module ->
    val interner = Interner.createStringInterner()
    module.addDeserializer(String::class.java, object : StringDeserializer() {
      override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
        return super.deserialize(p, ctxt)?.let { interner.intern(it) }
      }
    })
  })

@ApiStatus.Internal
fun InputStream.readWebTypes(): WebTypes =
  objectMapper.readValue(this, WebTypes::class.java)

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

  private fun get(versions: SortedMap<SemVer, T>?,
                  pkgVersion: SemVer?): T? {
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