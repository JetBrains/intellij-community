package org.jetbrains.completion.full.line.local

abstract class LocalModelsException(msg: String) : Exception(msg)

class MissingPartOfLocalModel : LocalModelsException("Local model must contains `.model|bin|onnx`, `.json` and `.bpe` files")

class LocalModelIsNotDirectory : LocalModelsException("Local model is not a directory")
