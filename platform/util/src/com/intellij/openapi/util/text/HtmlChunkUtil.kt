// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util.text

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun buildHtmlChunk(body: HtmlBuilder.() -> Unit): HtmlChunk = HtmlBuilder().apply(body).toFragment()

@ApiStatus.Experimental
operator fun HtmlChunk.plus(other: HtmlChunk): HtmlChunk = buildHtmlChunk {
  append(this@plus)
  append(other)
}

@ApiStatus.Experimental
inline fun HtmlChunk.Element.buildChildren(body: HtmlBuilder.() -> Unit): HtmlChunk.Element = HtmlBuilder().apply(body).wrapWith(this)

@ApiStatus.Experimental
inline fun buildHtml(body: HtmlBuilder.() -> Unit): @NlsSafe String = HtmlBuilder().apply(body).toString()
