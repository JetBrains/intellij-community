package com.intellij.grazie.config

import com.intellij.util.xmlb.annotations.Property

data class SuppressingContext(@Property val suppressed: Set<String> = HashSet())