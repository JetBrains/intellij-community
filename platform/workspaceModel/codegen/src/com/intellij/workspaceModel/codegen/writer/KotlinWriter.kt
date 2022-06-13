// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.patcher

import com.intellij.workspaceModel.codegen.deft.model.KtImports

class KotlinWriter(val src: CharSequence) {
  val result = StringBuilder()
  var pos = 0

  fun addTo(to: Int) {
    if (to < pos) return
    check(to >= pos) { "$to >= $pos" }
    result.append(src, pos, to)
    pos = to
  }

  fun skipTo(to: Int) {
    if (to < pos) return
    check(to >= pos) { "$to >= $pos" }
    pos = to
  }

  fun end() {
    addTo(src.length)
  }

  fun addImports(existed: KtImports, requiredImports: Collection<String>) {
    addTo(prevNonWs(existed.range.last) + 1)
    var needNl = false
    requiredImports.forEach {
      val i = it.trim()
      if (i.isNotEmpty()) {
        if (i !in existed.list) {
          result.append("\nimport $it")
          needNl = true
        }
      }
    }
    if (needNl) result.append("\n")
  }

  fun removeBlock(range: IntRange) {
    var start = prevNl(range.first)
    var end = nextNl(range.last)

    // remove empty "{ ... }" block
    val prev = prevNonWs(start)
    val next = nextNonWs(end)
    if (src[prev] == '{' && src[next] == '}') {
      start = prevNonWs(prev)
      end = next + 1
    }

    addTo(start)
    skipTo(end)
  }

  private fun prevNl(start: Int): Int {
    var p = start - 1
    var prevNl = p
    while (p != 0) {
      if (src[p] == '\n') prevNl = p
      if (!src[p].isWhitespace()) break
      p--
    }
    return prevNl
  }

  private fun nextNl(end: Int): Int {
    val nextNl = src.indexOf('\n', end)
    return if (nextNl == -1) end else nextNl
  }


  private fun prevNonWs(start: Int): Int {
    var p = start
    while (p != 0) {
      if (!src[p].isWhitespace()) break
      p--
    }
    return p
  }

  private fun nextNonWs(start: Int): Int {
    var p = start
    while (p != src.length) {
      if (!src[p].isWhitespace()) break
      p++
    }
    return p
  }
}