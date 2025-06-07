// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider

internal class GrazieDictionaryProvider : RuntimeDictionaryProvider {
  override fun getDictionaries(): Array<Dictionary> = arrayOf(GrazieDictionary)
}
