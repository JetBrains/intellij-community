// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import kotlin.jvm.JvmInline

@JvmInline
internal value class MyList<E> private constructor(
  private val arrayList: ArrayList<E> = ArrayList(),
) : Iterable<E> {
  constructor(initialCapacity: Int) : this(ArrayList<E>(initialCapacity))

  val size: Int
    get() = arrayList.size

  operator fun get(index: Int): E =
    arrayList[index]

  fun trimSize(fromIndex: Int) {
    arrayList.subList(fromIndex, this.size).clear()
  }

  fun add(e: E): Boolean {
    val size = this.size
    if (size >= MAX_VARIANTS_SIZE) {
      trimSize(MAX_VARIANTS_SIZE / 4)
    }
    return arrayList.add(e)
  }

  override fun iterator(): Iterator<E> =
    arrayList.iterator()
}