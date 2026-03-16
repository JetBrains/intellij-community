// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmField

@ApiStatus.Experimental
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

@ApiStatus.Experimental
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

@ApiStatus.Experimental
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