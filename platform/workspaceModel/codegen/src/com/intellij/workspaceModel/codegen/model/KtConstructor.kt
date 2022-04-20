// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

open class KtConstructor(
  val src: Src,
  val isStub: Boolean = false,
  val scope: KtScope? = null,
) {
  val children = mutableListOf<KtConstructor>()
  val defs = mutableListOf<DefField>()
  var _generatedCode: IntRange? = null
  val generatedCode: SrcRange? get() = _generatedCode?.let { src.range(it) }
  lateinit var prevElementEnd: SrcPos
  var range: SrcRange? = null
  val text: String? get() = range?.text

  //    val indent: String by lazy {
  //        val range = range ?: return@lazy defaultIndent()
  //
  //        val textStart = range.first.pos
  //        var textEnd = range.last.pos
  //        if (_generatedCode != null) textEnd = _generatedCode!!.start
  //
  //        val src = src.contents()
  //        var p = textStart
  //
  //        // skip all new lines
  //        while (p < textEnd && src[p] == '\n') p++
  //
  //        // skip line comments at line start
  //        while (true) {
  //            if (p + 1 < textEnd && src[p] == '/' && src[p + 1] == '/') {
  //                p += 2
  //                while (p < textEnd && src[p] != '\n') p++ // until line end
  //                while (p < textEnd && src[p] == '\n') p++ // skip all new lines
  //                continue
  //            }
  //            break
  //        }
  //
  //        val start = p
  //
  //        // get first line indent
  //        while (p < textEnd && src[p].isWhitespace()) p++
  //
  //        // only if the whole block is not empty
  //        if (p == textEnd) return@lazy defaultIndent()
  //
  //        src.substring(start, p)
  //    }
  //
  //    private fun defaultIndent() = if (parent == null) "" else parent.indent + "    "
}