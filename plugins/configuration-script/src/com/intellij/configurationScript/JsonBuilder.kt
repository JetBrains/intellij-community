package com.intellij.configurationScript

import org.jetbrains.io.JsonUtil

internal inline fun StringBuilder.json(build: JsonObjectBuilder.() -> Unit): StringBuilder {
  val builder = JsonObjectBuilder(this)
  appendCommaIfNeed()
  append('{')
  builder.build()
  append('}')
  return this
}

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

  fun rawScalar(key: CharSequence, build: StringBuilder.() -> Unit) {
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
      .append(':')
      .append('"')
      .build()
    builder.append('"')
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
    builder
      .appendCommaIfNeed()
      .jsonEscapedString(key)
      .append(':')
      .append('{')
      .append('\n')
    build(builder)
    builder
      .append('\n')
      .append('}')
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