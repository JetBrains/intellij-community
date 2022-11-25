package org.jetbrains.completion.full.line.local

open class CompletionException(message: String?) : RuntimeException(message)

abstract class LocalModelsException(msg: String) : Exception(msg)

class TooShortAllowedContextLength(message: String?) : CompletionException(message)

class MissingPartOfLocalModel : LocalModelsException("Local model must contains `.model|bin|onnx`, `.json` and `.bpe` files")

class LocalModelIsNotDirectory : LocalModelsException("Local model is not a directory")
