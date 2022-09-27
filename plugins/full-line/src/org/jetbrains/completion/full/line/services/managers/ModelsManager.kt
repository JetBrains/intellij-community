package org.jetbrains.completion.full.line.services.managers

import com.intellij.lang.Language
import org.jetbrains.completion.full.line.local.ModelSchema

interface ModelsManager {
  fun getLatest(language: Language, force: Boolean = false): ModelSchema

  fun download(language: Language, force: Boolean = false): ModelSchema

  fun update(language: Language, force: Boolean = false): ModelSchema
}
