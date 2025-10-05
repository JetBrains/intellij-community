// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.hunspell

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.ui.SpellCheckingNotifier
import com.intellij.spellchecker.util.SpellCheckerBundle
import java.io.FileNotFoundException
import java.text.ParseException

internal class HunspellDictionaryProvider : CustomDictionaryProvider {
  private fun isHunspellPluginInstalled(): Boolean {
    val hunspellId = PluginId.getId("hunspell")
    val ideaPluginDescriptor = PluginManagerCore.getPlugin(hunspellId)
    return PluginManagerCore.isPluginInstalled(hunspellId) && ideaPluginDescriptor != null && ideaPluginDescriptor.isEnabled
  }

  override fun get(dicPath: String): Dictionary? {
    try {
      if (isIncompleteHunspell(dicPath)) {
        val (dic, aff) = HunspellDictionary.getHunspellBundle(dicPath)
        SpellCheckingNotifier.showWarningNotificationBalloon(
          SpellCheckerBundle.message("dictionary.hunspell.incomplete.title"),
          SpellCheckerBundle.message("dictionary.hunspell.incomplete", dic.toPath().fileName, aff.toPath().fileName)
        )
        return null
      }
      if (isHungarian(dicPath)) {
        SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.unsupported.language.title"),
                                                             SpellCheckerBundle.message("dictionary.unsupported.language", dicPath))
        return null
      }
      return HunspellDictionary(dicPath)
    }
    catch (_: FileNotFoundException) {
      SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.not.found.title"),
                                                           SpellCheckerBundle.message("dictionary.not.found", dicPath))
    }
    catch (_: ParseException) {
      SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.unsupported.format.title"),
                                                           SpellCheckerBundle.message("dictionary.unsupported.format", dicPath))
    }

    return null
  }

  override fun isApplicable(path: String): Boolean {
    return (HunspellDictionary.isHunspell(path) || isIncompleteHunspell(path))
           && !(isHunspellPluginInstalled() && isHungarian(path))
  }

  override fun getDictionaryType() = SpellCheckerBundle.message("hunspell.dictionary")

  private fun isHungarian(path: String): Boolean {
    if (FileUtilRt.getExtension(path) != "dic") return false
    val (_, aff) = HunspellDictionary.getHunspellBundle(path)
    if (!aff.exists()) return false

    try {
      for (line in aff.readLines()) {
        val args = line.split("\\s+".toRegex())
        if (args.size == 2 && args[0] == "LANG") {
          return args[1] in setOf("hu", "HU", "hu_HU")
        }
      }
    }
    catch (_: Exception) {
    }

    return false
  }

  private fun isIncompleteHunspell(path: String): Boolean {
    if (FileUtilRt.getExtension(path) != "dic") return false
    val (dic, aff) = HunspellDictionary.getHunspellBundle(path)

    if (dic.exists() && !aff.exists()) {
      try {
        for (line in dic.readLines()) {
          if (line.contains('/')) return true
        }
      }
      catch (_: Exception) {
      }
    }
    return false
  }
}
