// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime.impl

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.util.runtime.BracePair
import com.intellij.platform.syntax.util.runtime.ErrorState
import com.intellij.platform.syntax.util.runtime.Frame
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxRuntimeBundle
import com.intellij.util.containers.LimitedPool

internal class ErrorStateImpl : ErrorState {
  internal var currentFrame: FrameImpl? = null
  internal val variants: MyList<Variant> = MyList(INITIAL_VARIANTS_SIZE)
  internal val unexpected: MyList<Variant> = MyList(INITIAL_VARIANTS_SIZE / 10)

  internal var predicateCount: Int = 0
  internal var level: Int = 0
  internal var predicateSign: Boolean = true
  internal var suppressErrors: Boolean = false
  internal var hooks: ArrayDeque<HookBatch<*>> = ArrayDeque(8)

  internal var extendsSets: Array<SyntaxElementTypeSet> = emptyArray()
  internal var braces: Array<BracePair> = emptyArray()
  internal var altMode: Boolean = false

  internal val variantPool: LimitedPool<Variant> = LimitedPool(VARIANTS_POOL_SIZE) { Variant() }
  internal val framePool: LimitedPool<FrameImpl> = LimitedPool(FRAMES_POOL_SIZE) { FrameImpl() }

  override fun getExpected(position: Int, expected: Boolean): String {
    val list = if (expected) variants else unexpected

    val strings = list.asSequence()
      .filter { it.position == position }
      .mapNotNull { it.`object`?.toString() }
      .filter { it.isNotEmpty() }
      .distinct()
      .sorted()
      .toList()

    fun escape(s: String): String {
      val firstLetter = s[0]
      return if (firstLetter == '$' || firstLetter == '_' || firstLetter == '<' || firstLetter.isLetter()) {
        s
      }
      else {
        "'$s'"
      }
    }

    return when {
      strings.size > MAX_VARIANTS_TO_DISPLAY -> {
        strings
          .take(MAX_VARIANTS_TO_DISPLAY)
          .joinToString(separator = ", ", postfix = " ${SyntaxRuntimeBundle.message("parsing.error.and.ellipsis")}") { s -> escape(s) }
      }
      strings.size > 1 -> {
        val sorted = strings.map { s -> escape(s) }
        val last = sorted.last()
        sorted
          .dropLast(1)
          .joinToString(separator = ", ", postfix = " ${SyntaxRuntimeBundle.message("parsing.error.or")} $last")
      }
      else -> {
        strings.singleOrNull()?.let { escape(it) }.orEmpty()
      }
    }
  }

  override fun clearVariants(frame: Frame?) {
    clearVariants(true, frame?.variantCount ?: 0)
    if (frame != null) {
      (frame as FrameImpl).lastVariantAt = -1
    }
  }

  override fun clearVariants(expected: Boolean, start: Int) {
    val list: MyList<Variant> = if (expected) variants else unexpected
    if (start < 0 || start >= list.size) return
    var i = start
    val len: Int = list.size
    while (i < len) {
      variantPool.recycle(list[i])
      i++
    }
    list.trimSize(start)
  }

  override fun typeExtends(child: SyntaxElementType?, parent: SyntaxElementType?): Boolean {
    if (child === parent) return true
    return extendsSets.any { it.contains(child) && it.contains(parent) }
  }

  fun initState(runtime: SyntaxGeneratedParserRuntime, extendsSets: Array<SyntaxElementTypeSet>) {
    this.extendsSets = extendsSets
    this.braces = (runtime as SyntaxGeneratedParserRuntimeImpl).braces.toTypedArray()
  }
}