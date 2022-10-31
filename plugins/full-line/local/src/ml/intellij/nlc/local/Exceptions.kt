package ml.intellij.nlc.local

open class CompletionException(message: String?): RuntimeException(message)

class LongLastLineException: CompletionException("Last line is too long")