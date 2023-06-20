// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import java.util.*

data class Session(val offset: Int,
                   val expectedText: String,
                   val completableLength: Int,
                   private var _properties: TokenProperties,
                   val id: String = UUID.randomUUID().toString()) {
  constructor(other: Session) : this(other.offset, other.expectedText, other.completableLength, other._properties, other.id)

  private val _lookups = mutableListOf<Lookup>()

  val lookups: List<Lookup>
    get() = _lookups

  val properties: TokenProperties
    get() = _properties

  fun addLookup(lookup: Lookup) {
    _lookups.add(lookup)

    val features = mutableSetOf<String>()
    lookup.features?.common?.context?.keys?.let { features.addAll(it) }
    lookup.features?.common?.session?.keys?.let { features.addAll(it) }
    lookup.features?.common?.user?.keys?.let { features.addAll(it) }
    lookup.features?.element?.forEach { features.addAll(it.keys) }
    _properties = _properties.withFeatures(features)
  }

  fun removeLookup(lookup: Lookup) = _lookups.remove(lookup)
  fun getFeatures(): List<Features> = _lookups.map { it.features ?: Features.EMPTY }
  fun clearFeatures() = _lookups.forEach { it.clearFeatures() }
}
