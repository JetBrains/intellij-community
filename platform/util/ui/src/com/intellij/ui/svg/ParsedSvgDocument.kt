// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.DomElement
import com.github.weisj.jsvg.parser.DomProcessor
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Result of parsing an SVG document for IDEA's icon rendering pipeline.
 *
 * Wraps the upstream [SVGDocument] together with IDEA-specific metadata captured from the raw
 * root `<svg>` attributes. The [isDataScaled] flag, the original `width`/`height` attribute
 * strings, and the knowledge of whether a `viewBox` was declared are not recoverable from the
 * [SVGDocument] public API after parsing — jsvg applies defaults and exposes only resolved
 * values — so we capture them here during parse.
 */
@Internal
class ParsedSvgDocument internal constructor(
  val document: SVGDocument,
  /**
   * Mirrors the non-standard JetBrains `data-scaled="true"` attribute on the root `<svg>`.
   * See `docs/IntelliJ-Platform/4_man/UI/HiDPI-%2F-Scaling-in-IntelliJ-Platform.md` for details.
   */
  val isDataScaled: Boolean,
  /** Raw value of the `width` attribute on the root `<svg>`, or `null` if absent. */
  val rawWidth: String?,
  /** Raw value of the `height` attribute on the root `<svg>`, or `null` if absent. */
  val rawHeight: String?,
  /** Raw value of the `viewBox` attribute on the root `<svg>`, or `null` if absent. */
  val rawViewBox: String?,
)

/**
 * Captures IDEA-specific attributes from the root `<svg>` element during parse.
 *
 * Create a fresh instance per [com.github.weisj.jsvg.parser.LoaderContext]; jsvg invokes
 * [process] exactly once per parse with the root as its argument.
 */
internal class RootAttributeCollector : DomProcessor {
  private var capturedIsDataScaled: Boolean = false
  private var capturedRawWidth: String? = null
  private var capturedRawHeight: String? = null
  private var capturedRawViewBox: String? = null

  val isDataScaled: Boolean get() = capturedIsDataScaled
  val rawWidth: String? get() = capturedRawWidth
  val rawHeight: String? get() = capturedRawHeight
  val rawViewBox: String? get() = capturedRawViewBox

  override fun process(root: DomElement) {
    capturedIsDataScaled = root.attribute("data-scaled").toBoolean()
    capturedRawWidth = root.attribute("width")
    capturedRawHeight = root.attribute("height")
    capturedRawViewBox = root.attribute("viewBox")
  }
}
