// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import com.intellij.grazie.GrazieBundle
import com.intellij.openapi.components.service
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus
import com.intellij.util.Consumer

internal object GrazieDictionary : Dictionary {
  override fun getName() = GrazieBundle.message("grazie.spellcheck.dictionary.name")

  override fun lookup(word: String): LookupStatus = service<GrazieCheckers>().lookup(word)

  override fun consumeSuggestions(word: String, consumer: Consumer<String>) {
    for (it in service<GrazieCheckers>().getSuggestions(word)) {
      consumer.consume(it)
    }
  }

  override fun getWords() = throw UnsupportedOperationException()
}
