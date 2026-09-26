// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import kotlin.jvm.JvmField

@JvmField
val LEFT_BINDER: Hook<WhitespacesAndCommentsBinder> = object : Hook<WhitespacesAndCommentsBinder> {
  override fun run(
    parserRuntime: SyntaxGeneratedParserRuntime,
    marker: SyntaxTreeBuilder.Marker?,
    param: WhitespacesAndCommentsBinder,
  ): SyntaxTreeBuilder.Marker? {
    marker?.setCustomEdgeTokenBinders(param, null)
    return marker
  }
}

@JvmField
val RIGHT_BINDER: Hook<WhitespacesAndCommentsBinder> = object : Hook<WhitespacesAndCommentsBinder> {
  override fun run(
    parserRuntime: SyntaxGeneratedParserRuntime,
    marker: SyntaxTreeBuilder.Marker?,
    param: WhitespacesAndCommentsBinder,
  ): SyntaxTreeBuilder.Marker? {
    marker?.setCustomEdgeTokenBinders(null, param)
    return marker
  }
}

@JvmField
val WS_BINDERS: Hook<Array<WhitespacesAndCommentsBinder>> = object : Hook<Array<WhitespacesAndCommentsBinder>> {
  override fun run(
    parserRuntime: SyntaxGeneratedParserRuntime,
    marker: SyntaxTreeBuilder.Marker?,
    param: Array<WhitespacesAndCommentsBinder>,
  ): SyntaxTreeBuilder.Marker? {
    marker?.setCustomEdgeTokenBinders(param[0], param[1])
    return marker
  }
}