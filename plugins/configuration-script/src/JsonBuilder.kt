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
internal class JsonObjectBuilder(private val builder: StringBuilder) {
  infix fun String.to(value: String) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(this)
      .append(':')
      .jsonEscapedString(value)
  }

  infix fun String.toUnescaped(value: String) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(this)
      .append(':')
    JsonUtil.escape(value, builder)
  }

  infix fun String.to(value: Boolean) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(this)
      .append(':')
      .append(value)
  }

  infix fun String.to(value: StringBuilder) {
    if (value === builder) {
      return
    }

    builder
      .appendCommaIfNeed()
      .jsonEscapedString(this)
      .append(':')
      // append as is
      .append(value)
  }

  infix fun String.toRaw(value: String) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(this)
      .append(':')
      // append as is
      .append(value)
  }

  fun map(key: CharSequence, build: JsonObjectBuilder.() -> Unit) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
      .append(':')
      .append('{')
      .append('\n')
    this.build()
    builder
      .append('\n')
      .append('}')
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
      .append(':')
      .append(openChar)
      .append('\n')
    build(builder)
    builder
      .append('\n')
      .append(closeChar)
  }

  fun rawBuilder(key: CharSequence, child: JsonObjectBuilder) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
      .append(':')
      .append('{')
      .append('\n')
      .append(child.builder)
      .append('\n')
      .append('}')
  }

  fun definitionReference(prefix: String, pointer: CharSequence) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString("\$ref")
      .append(':')
      .append('"')
      .append(prefix)
      .append(pointer)
      .append('"')
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