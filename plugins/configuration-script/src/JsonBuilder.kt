package com.intellij.configurationScript

import org.jetbrains.io.JsonUtil

@DslMarker
annotation class JsonBuilderDsl

internal inline fun StringBuilder.json(build: JsonObjectBuilder.() -> Unit): StringBuilder {
  val builder = JsonObjectBuilder(this)
  appendCommaIfNeeded()
  builder.appendComplexValue('{', '}', isColonRequired = false) {
    builder.build()
  }
  return this
}

@JsonBuilderDsl
internal class JsonObjectBuilder(private val builder: StringBuilder, private var indentLevel: Int = 0, private val indent: String? = "  ") {
  inline fun StringBuilder.json(build: JsonObjectBuilder.() -> Unit): StringBuilder {
    val builder = JsonObjectBuilder(this, indentLevel = indentLevel + 1)
    appendCommaIfNeeded()
    builder.appendComplexValue('{', '}', isColonRequired = false) {
      builder.build()
    }
    return this
  }

  infix fun String.to(value: String) {
    appendNameAndValue(this) {
      builder.jsonEscapedString(value)
    }
  }

  infix fun String.toUnescaped(value: String) {
    appendNameAndValue(this) {
      JsonUtil.escape(value, builder)
    }
  }

  infix fun String.to(value: Boolean) {
    appendNameAndValue(this) {
      builder.append(value)
    }
  }

  infix fun String.to(value: StringBuilder) {
    if (value === builder) {
      return
    }

    appendNameAndValue(this) {
      // append as is
      builder.append(value)
    }
  }

  infix fun String.toRaw(value: String) {
    appendNameAndValue(this) {
      // append as is
      builder.append(value)
    }
  }

  fun map(key: CharSequence, build: JsonObjectBuilder.() -> Unit) {
    indentLevel++
    appendNameAndValue(key, '{', '}') {
      build()
    }
    indentLevel--
  }

  fun rawMap(key: CharSequence, build: (StringBuilder) -> Unit) {
    mapOrArray('{', '}', key, build)
  }

  fun rawArray(key: CharSequence, build: (StringBuilder) -> Unit) {
    mapOrArray('[', ']', key, build)
  }

  private fun mapOrArray(openChar: Char, closeChar: Char, key: CharSequence, build: (StringBuilder) -> Unit) {
    indentLevel++
    appendNameAndValue(key, openChar, closeChar) {
      build(builder)
    }
    indentLevel--
  }

  fun rawBuilder(key: CharSequence, child: JsonObjectBuilder) {
    appendNameAndValue(key, '{', '}') {
      builder.append(child.builder)
    }
  }

  fun definitionReference(prefix: String, pointer: CharSequence, wrappingKey: String? = null) {
    builder.appendCommaIfNeeded()

    if (wrappingKey != null) {
      builder.append('"').append(wrappingKey).append('"').append(':').append(' ')
    }

    builder.jsonEscapedString("\$ref")
    appendColon()
    builder.append('"')
      .append(prefix)
      .append(pointer)
      .append('"')
  }

  private fun appendColon() {
    builder.append(':')
    if (indent != null) {
      builder.append(' ')
    }
  }

  private inline fun appendNameAndValue(name: CharSequence, valueAppender: () -> Unit) {
    appendCommaIfNeeded(builder)
    builder.jsonEscapedString(name)
    appendColon()
    valueAppender()
  }

  private inline fun appendNameAndValue(key: CharSequence, openChar: Char, closeChar: Char, isColonRequired: Boolean = true, valueAppender: () -> Unit) {
    if (appendCommaIfNeeded(builder) && indent != null) {
      builder.append('\n')
      for (i in 1..indentLevel) {
        builder.append(indent)
      }
    }
    builder.jsonEscapedString(key)
    appendComplexValue(openChar, closeChar, isColonRequired, valueAppender)
  }

  internal inline fun appendComplexValue(openChar: Char, closeChar: Char, isColonRequired: Boolean = true, valueAppender: () -> Unit) {
    if (isColonRequired) {
      appendColon()
    }

    builder.append(openChar)

    if (indent != null) {
      builder.append('\n')
      for (i in 1..(indentLevel + 1)) {
        builder.append(indent)
      }
    }

    valueAppender()

    if (indent != null) {
      builder.append('\n')
      for (i in 1..indentLevel) {
        builder.append(indent)
      }
    }
    builder.append(closeChar)
  }
}

private fun appendCommaIfNeeded(builder: StringBuilder): Boolean {
  if (builder.isEmpty()) {
    return false
  }

  val lastChar = builder.last()
  if (lastChar == '"' || lastChar == '}' || lastChar == ']' || lastChar == 'e' /* true or false */) {
    builder.append(',')
    return true
  }
  return false
}

private fun StringBuilder.appendCommaIfNeeded(): StringBuilder {
  appendCommaIfNeeded(this)
  return this
}

private fun StringBuilder.jsonEscapedString(value: CharSequence): StringBuilder {
  append('"').append(value).append('"')
  return this
}