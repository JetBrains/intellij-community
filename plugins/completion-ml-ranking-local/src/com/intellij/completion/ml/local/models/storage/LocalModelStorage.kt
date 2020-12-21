package com.intellij.completion.ml.local.models.storage

interface LocalModelStorage {
  fun version(): Int
  fun name(): String
  fun isValid(): Boolean
  fun setValid(isValid: Boolean)
}