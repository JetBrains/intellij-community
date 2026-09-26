package com.intellij.ui.mac.foundation

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT

internal data class ObjcType(val code: Char, val layout: MemoryLayout?)

internal data class ObjcSignature(val result: ObjcType, val arguments: List<ObjcType>) {
  val descriptor: FunctionDescriptor =
    result.layout?.let { FunctionDescriptor.of(it, *arguments.map { type -> requireNotNull(type.layout) }.toTypedArray()) }
    ?: FunctionDescriptor.ofVoid(*arguments.map { requireNotNull(it.layout) }.toTypedArray())
}

internal class ObjcTypeEncoding(private val encoding: String) {
  private var position = 0

  fun signature(): ObjcSignature {
    val result = type()
    offset()
    val arguments = mutableListOf<ObjcType>()
    while (position < encoding.length) {
      arguments.add(type())
      offset()
    }
    return ObjcSignature(result, arguments)
  }

  private fun offset() {
    if (encoding.getOrNull(position) == '-') position++
    while (encoding.getOrNull(position)?.isDigit() == true) position++
  }

  private fun quotedName() {
    if (encoding.getOrNull(position) != '"') return
    position++
    while (encoding.getOrNull(position) != '"') {
      require(position < encoding.length) { "Incomplete Objective-C type: $encoding" }
      position++
    }
    position++
  }

  private fun type(required: Boolean = true): ObjcType {
    while (encoding.getOrNull(position) in listOf('r', 'n', 'N', 'o', 'O', 'R', 'V')) position++
    quotedName()
    require(position < encoding.length) { "Incomplete Objective-C type: $encoding" }
    val code = encoding[position++]
    val layout = when (code) {
      'v' -> null
      'c', 'C', 'B' -> JAVA_BYTE
      's', 'S' -> JAVA_SHORT
      'i', 'I', 'l', 'L' -> JAVA_INT
      'q', 'Q' -> JAVA_LONG
      'f' -> JAVA_FLOAT
      'd' -> JAVA_DOUBLE
      '*', ':', '#' -> ADDRESS
      '@' -> {
        if (encoding.getOrNull(position) == '?') position++ else quotedName()
        ADDRESS
      }
      '^' -> {
        type(false)
        ADDRESS
      }
      '{', '(' -> {
        val closing = if (code == '{') '}' else ')'
        while (encoding.getOrNull(position) !in listOf('=', closing, null)) position++
        val members = mutableListOf<MemoryLayout>()
        if (encoding.getOrNull(position) == '=') {
          position++
          while (encoding.getOrNull(position) != closing) {
            val member = type(required)
            if (required) members.add(requireNotNull(member.layout))
          }
        }
        require(encoding.getOrNull(position++) == closing) { "Incomplete Objective-C type: $encoding" }
        if (required) {
          require(code == '{' && members.isNotEmpty()) { "Unsupported Objective-C type: $encoding" }
          structure(members)
        }
        else null
      }
      '[' -> {
        val start = position
        offset()
        val count = encoding.substring(start, position).toLong()
        val element = type(required)
        require(encoding.getOrNull(position++) == ']') { "Incomplete Objective-C array: $encoding" }
        if (required) MemoryLayout.sequenceLayout(count, requireNotNull(element.layout)) else null
      }
      else -> {
        require(!required) { "Unsupported Objective-C type '$code': $encoding" }
        null
      }
    }
    return ObjcType(code, layout)
  }

  private fun structure(members: List<MemoryLayout>): MemoryLayout {
    val layouts = mutableListOf<MemoryLayout>()
    var size = 0L
    val alignment = members.maxOf { it.byteAlignment() }
    for (member in members) {
      val padding = Math.floorMod(-size, member.byteAlignment())
      if (padding != 0L) layouts.add(MemoryLayout.paddingLayout(padding))
      layouts.add(member)
      size += padding + member.byteSize()
    }
    val padding = Math.floorMod(-size, alignment)
    if (padding != 0L) layouts.add(MemoryLayout.paddingLayout(padding))
    return MemoryLayout.structLayout(*layouts.toTypedArray())
  }
}
