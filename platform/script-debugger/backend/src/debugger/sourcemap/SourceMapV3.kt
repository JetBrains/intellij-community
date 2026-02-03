// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger.sourcemap

/**
 * Intermediate representation of the source map used during parsing
 */
internal sealed class SourceMapV3 {
  abstract val version: Int
  abstract val file: String?
}

internal class FlatSourceMap(
  override val version: Int,
  override val file: String?,
  val sources: List<String?>,
  val sourcesContent: List<String?>?,
  val sourceRoot: String?,
  val names: List<String>?,
  /**
   * This field is intentionally made CharSequence instead of String to be able
   * to reuse the input source map string during parsing and avoid allocation
   */
  val mappings: CharSequence,
  val ignoreList: List<Int>?,
) : SourceMapV3()

internal class SectionedSourceMap(
  override val version: Int,
  override val file: String?,
  val sections: List<Section>,
) : SourceMapV3()

internal class Section(
  val offset: Offset,
  val map: SourceMapV3,
)

internal data class Offset(
  val line: Int,
  val column: Int,
)