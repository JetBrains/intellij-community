package org.jetbrains.completion.full.line.local

open class CompletionException(message: String?) : RuntimeException(message)

class LongLastLineException : CompletionException("Last line is too long")
