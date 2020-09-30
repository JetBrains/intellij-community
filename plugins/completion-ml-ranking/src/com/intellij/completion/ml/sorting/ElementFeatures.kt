package com.intellij.completion.ml.sorting

data class ElementFeatures(val relevance: Map<String, Any>, val additional: MutableMap<String, Any>)