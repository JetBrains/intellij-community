package org.jetbrains.completion.full.line.local

open class CompletionException(message: String?) : RuntimeException(message)

class LongLastLineException : CompletionException("Last line is too long")

abstract class LocalModelsException(msg: String) : Exception(msg)

class MissingPartOfLocalModel : LocalModelsException("Local model must contains `.model|bin|onnx`, `.json` and `.bpe` files")

class LocalModelIsNotDirectory : LocalModelsException("Local model is not a directory")
