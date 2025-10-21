package com.intellij.grazie.mlec

import ai.grazie.nlp.langs.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

abstract class LanguageHolder<T: Disposable>: Disposable {
  private val holder = ConcurrentHashMap<Language, T>()

  fun update(language: Language, value: T) {
    holder[language] = value
  }

  fun update(all: Map<Language, T>) {
    holder.putAll(all)
  }

  fun get(language: Language): T? = holder[language]

  override fun dispose() {
    holder.values.forEach { Disposer.dispose(it) }
  }
}