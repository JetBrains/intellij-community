// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

open class KtBlock(
  val src: Src,
  val parent: KtBlock?,
  val isStub: Boolean = false,
  val scope: KtScope? = null,
) {
  val children = mutableListOf<KtBlock>()
  val defs = mutableListOf<DefField>()
  var _generatedCode: IntRange? = null
  var _extensionCode: IntRange? = null
  val generatedCode: SrcRange? get() = _generatedCode?.let { src.range(it) }
  var range: SrcRange? = null
  val text: String? get() = range?.text

  val indent: String by lazy {
    val range = range ?: return@lazy defaultIndent()

    val textStart = range.first.pos
    var textEnd = range.last.pos
    if (_generatedCode != null) textEnd = _generatedCode!!.start

    val src = src.contents()
    var p = textStart

    // skip all new lines
    while (p < textEnd && src[p] == '\n') p++

    // skip line comments at line start
    while (true) {
      if (p + 1 < textEnd && src[p] == '/' && src[p + 1] == '/') {
        p += 2
        while (p < textEnd && src[p] != '\n') p++ // until line end
        while (p < textEnd && src[p] == '\n') p++ // skip all new lines
        continue
      }
      break
    }

    val start = p

    // get first line indent
    while (p < textEnd && src[p].isWhitespace()) p++

    // only if the whole block is not empty
    if (p == textEnd) return@lazy defaultIndent()

    src.substring(start, p)
  }

  fun isInsideGenerateBlock(): Boolean {
    if (range == null) return false
    val intRange = range!!.range
    if (parent == null) return false
    val generatedCodeRange = parent._generatedCode
    if (generatedCodeRange == null) return false
    return generatedCodeRange.first <= intRange.first && intRange.last <= generatedCodeRange.last
  }

  private fun defaultIndent() = if (parent == null) "" else parent.indent + "    "
}