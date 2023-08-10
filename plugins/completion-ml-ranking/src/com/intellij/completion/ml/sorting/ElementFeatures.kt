package com.intellij.completion.ml.sorting

data class ElementFeatures(val relevance: MutableMap<String, Any>, val additional: MutableMap<String, Any>)