package com.intellij.cce.core

enum class TokenLocationProperty(val key: String) {
  FILE("file_name"),
  CLASS("class_name"),
  METHOD("method_name"),
  METHOD_QUALIFIED_NAME("method_qualified_name"),
  TEST_SOURCE("test_source"),
}