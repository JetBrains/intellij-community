package com.intellij.cce.core

data class CommonFeatures(val context: Map<String, String>,
                          val user: Map<String, String>,
                          val session: Map<String, String>)

data class Features(val common: CommonFeatures, val element: List<Map<String, Any>>) {
  companion object {
    val EMPTY: Features = Features(CommonFeatures(emptyMap(), emptyMap(), emptyMap()), emptyList())
  }
}