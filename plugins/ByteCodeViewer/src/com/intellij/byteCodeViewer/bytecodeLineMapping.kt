// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.util.Textifier
import java.util.Scanner

/**
 * This method removes the following debug information from `bytecode`:
 *  - `LINENUMBER`
 *  - `LOCALVARIABLE`
 *
 * ### Why is this needed?
 *
 * Ideally, to get bytecode without debug information, we would use [ClassReader] from the ASM library, and parse bytecode with the [ClassReader.SKIP_DEBUG] flag.
 * Unfortunately, this flag doesn't achieve what we want because it removes labels from the bytecode.
 *
 * This is bad because labels are often targets of conditional jumps.
 * Also, we do want to display labels.
 * They're useful.
 *
 * @param bytecodeWithDebugInfo - bytecode returned by ASM [ClassReader] and [Textifier] with [ClassReader.SKIP_FRAMES] flag.
 * @see BytecodeToolWindowPanel.deserializeBytecode
 */
internal fun removeDebugInfo(bytecodeWithDebugInfo: String): String = bytecodeWithDebugInfo.lines()
  .filter { line -> !isDebugLine(line.trim()) }
  .joinToString("\n")


/**
 * Maps the line numbers from the provided bytecode to the source code line numbers within a specified range.
 *
 * @param bytecodeWithDebugInfo The Java bytecode in ASM format, with debug information included (see [ClassReader.SKIP_DEBUG])
 * @param sourceStartLine The starting line number in the source code to map from.
 * @param sourceEndLine The ending line number in the source code to map to.
 * @return A pair where the first element is the start line number in the bytecode, and the second element is the end line number in the bytecode. Returns (0, 0) if no valid mapping
 *  is found.
 */
internal fun mapLines(bytecodeWithDebugInfo: String, sourceStartLine: Int, sourceEndLine: Int, showDebugInfo: Boolean): IntRange {
  var sourceStartLine = sourceStartLine // editor selection is 0-indexed
  var currentBytecodeLine = 0
  var bytecodeStartLine = -1
  var bytecodeEndLine = -1

  val lineNumbers = arrayListOf<Int>()
  for (line in bytecodeWithDebugInfo.split("\n").dropLastWhile { it.isEmpty() }.map { line -> line.trim { it <= ' ' } }) {
    if (line.startsWith("LINENUMBER")) {
      // `line` is e.g. "LINENUMBER 3 L0" or "LINENUMBER 6 L1", but we are only interested in the 3 or 6, respectively.
      val sourceLineNumber = Scanner(line.substring("LINENUMBER".length)).nextInt() - 1
      lineNumbers.add(sourceLineNumber)
    }
  }
  lineNumbers.sort()

  for (line in lineNumbers) {
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

  if (!showDebugInfo) {
    bytecodeStartLine -= linesToSkipBeforeStartLine
    bytecodeEndLine -= linesToSkipBeforeEndLine
  }
  else {
    bytecodeStartLine -= 1
    bytecodeEndLine -= 1
  }

  return if (bytecodeStartLine == -1 || bytecodeEndLine == -1) IntRange(0, 0) else IntRange(bytecodeStartLine, bytecodeEndLine)
}

/**
 * Returns true if `line` is considered to be part of debug info, i.e., not actual bytecode.
 */
private fun isDebugLine(line: String): Boolean {
  if (line.startsWith("LINENUMBER")) return true
  if (line.startsWith("LOCALVARIABLE")) return true
  return false
}
