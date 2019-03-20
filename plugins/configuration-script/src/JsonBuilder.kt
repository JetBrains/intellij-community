package com.intellij.configurationScript

import org.jetbrains.io.JsonUtil

@DslMarker
annotation class JsonBuilderDsl

internal inline fun StringBuilder.json(build: JsonObjectBuilder.() -> Unit): StringBuilder {
  val builder = JsonObjectBuilder(this)
  appendCommaIfNeed()
  append('{')
  builder.build()
  append('}')
  return this
}

@JsonBuilderDsl
internal class JsonObjectBuilder(private val builder: StringBuilder, private val isCompact: Boolean = false) {
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
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)

    appendComplexValue('{', '}') {
      build()
    }
  }

  fun rawMap(key: CharSequence, build: (StringBuilder) -> Unit) {
    mapOrArray('{', '}', key, build)
  }

  fun rawArray(key: CharSequence, build: (StringBuilder) -> Unit) {
    mapOrArray('[', ']', key, build)
  }

  private fun mapOrArray(openChar: Char, closeChar: Char, key: CharSequence, build: (StringBuilder) -> Unit) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
    appendComplexValue(openChar, closeChar) {
      build(builder)
    }
  }

  fun rawBuilder(key: CharSequence, child: JsonObjectBuilder) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
    appendComplexValue('{', '}') {
      builder.append(child.builder)
    }
  }

  fun definitionReference(prefix: String, pointer: CharSequence, wrappingKey: String? = null) {
    builder.appendCommaIfNeed()

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
    if (!isCompact) {
      builder.append(' ')
    }
  }

  private inline fun appendNameAndValue(name: CharSequence, valueAppender: () -> Unit) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(name)
    appendColon()
    valueAppender()
  }

  private inline fun appendComplexValue(openChar: Char, closeChar: Char, valueAppender: () -> Unit) {
    appendColon()
    builder.append(openChar)
    if (!isCompact) {
      builder
        .append('\n')
        .append(' ')
        .append(' ')
    }

    valueAppender()

    if (!isCompact) {
      builder.append('\n')
    }
    builder.append(closeChar)
  }
}

private fun StringBuilder.appendCommaIfNeed(): StringBuilder {
  if (isEmpty()) {
    return this
  }

  val lastChar = last()
  if (lastChar == '"' || lastChar == '}' || lastChar == ']' || lastChar == 'e' /* true or false */) {
    append(',')
  }
  return this
}

private fun StringBuilder.jsonEscapedString(value: CharSequence): StringBuilder {
  append('"').append(value).append('"')
  return this
}