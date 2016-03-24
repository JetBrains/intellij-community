package org.jetbrains.debugger.sourcemap

/**
 * Mapping entry in the source map
 */
interface MappingEntry {
  val generatedColumn: Int

  val generatedLine: Int

  val sourceLine: Int

  val sourceColumn: Int

  val source: Int
    get() = -1

  val name: String?
    get() = null
}
