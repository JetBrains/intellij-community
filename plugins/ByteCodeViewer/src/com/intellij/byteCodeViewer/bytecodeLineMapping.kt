// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import java.util.*


/**
 * This method the following debugging information from `bytecode`:
 *  - `LINENUMBER`
 *  - `LOCALVARIABLE`
 *
 * ### Why is this needed?
 *
 * Ideally, we would use `ClassReader#SKIP_DEBUG` flag, but it has problematic behavior when it comes to labels.
 *
 * When parsing bytecode with ASM's `ClassReader`, we want to set `ClassReader#SKIP_DEBUG`, because it's actually not part of bytecode.
 * Unfortunately, `ClassReader#SKIP_DEBUG` also removes labels from the bytecode in most cases.
 * This is bad because labels are often targets of conditional jumps.
 * Also, we do want to display labels.
 * They're useful.
 *
 */
internal fun removeDebugInfo(bytecodeWithDebugInfo: String): String = bytecodeWithDebugInfo.lines()
  .filter { line -> !isDebugLine(line.trim()) }
  .joinToString("\n")


/**
 * Maps the line numbers from the provided bytecode to the source code line numbers within a specified range.
 *
 * @param bytecodeWithDebugInfo The Java bytecode in ASM format, with debugging information included (see `ClassReader#SKIP_DEBUG`)
 * @param sourceStartLine The starting line number in the source code to map from.
 * @param sourceEndLine The ending line number in the source code to map to.
 * @return A pair where the first element is the start line number in the bytecode, and the second element is the end line number in the bytecode. Returns (0, 0) if no valid mapping
 *  is found.
 */
internal fun mapLines(bytecodeWithDebugInfo: String, sourceStartLine: Int, sourceEndLine: Int, stripDebugInfo: Boolean = false): IntRange {
  var sourceStartLine = sourceStartLine // + 1 // editor selection is 0-indexed
  var currentBytecodeLine = 0
  var bytecodeStartLine = -1
  var bytecodeEndLine = -1

  val lines = arrayListOf<Int>()
  for (line in bytecodeWithDebugInfo.split("\n").dropLastWhile { it.isEmpty() }.map { line -> line.trim { it <= ' ' } }) {
    if (line.startsWith("LINENUMBER")) {
      // `line` is e.g. "LINENUMBER 3 L0" or "LINENUMBER 6 L1", but we are only interested in the 3 or 6, respectively.
      val ktLineNum = Scanner(line.substring("LINENUMBER".length)).nextInt() - 1
      lines.add(ktLineNum)
    }
  }
  lines.sort()

  for (line in lines) {
    if (line >= sourceStartLine) {
      sourceStartLine = line
      break
    }
  }

  var linesToSkipBeforeStartLine = 0
  var linesToSkipBeforeEndLine = 0

  for (line in bytecodeWithDebugInfo.split("\n").dropLastWhile { it.isEmpty() }.map { line -> line.trim { it <= ' ' } }) {
    if (bytecodeEndLine < 0 && isDebugLine(line)) {
      linesToSkipBeforeEndLine++
    }

    if (bytecodeStartLine < 0 && isDebugLine(line)) {
      linesToSkipBeforeStartLine++
    }

    if (line.startsWith("LINENUMBER")) {
      val ktLineNum = Scanner(line.substring("LINENUMBER".length)).nextInt() - 1

      if (bytecodeStartLine < 0 && ktLineNum == sourceStartLine) {
        bytecodeStartLine = currentBytecodeLine
      }

      if (bytecodeStartLine > 0 && ktLineNum > sourceEndLine) {
        bytecodeEndLine = currentBytecodeLine - 1
        break
      }
    }

    if (bytecodeStartLine >= 0 && (line.startsWith("MAXSTACK") || line.startsWith("LOCALVARIABLE") || line.isEmpty())) {
      // We have reached the end of the method body
      bytecodeEndLine = currentBytecodeLine - 1
      break
    }

    currentBytecodeLine++
  }

  if (stripDebugInfo) {
    bytecodeStartLine -= linesToSkipBeforeStartLine
    bytecodeEndLine -= linesToSkipBeforeEndLine
  }

  return if (bytecodeStartLine == -1 || bytecodeEndLine == -1) IntRange(0, 0) else IntRange(bytecodeStartLine, bytecodeEndLine)
}

/**
 * Returns true if `line` is considered to be part of debug info, i.e. not actual bytecode.
 */
private fun isDebugLine(line: String): Boolean {
  if (line.startsWith("LINENUMBER")) return true
  if (line.startsWith("LOCALVARIABLE")) return true
  return false
}
