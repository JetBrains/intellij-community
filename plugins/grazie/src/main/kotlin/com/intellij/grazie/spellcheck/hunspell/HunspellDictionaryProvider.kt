// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.hunspell

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.ui.SpellCheckingNotifier
import com.intellij.spellchecker.util.SpellCheckerBundle
import com.intellij.util.text.StringSearcher
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.text.ParseException

private val LOG = Logger.getInstance(HunspellDictionaryProvider::class.java)

internal class HunspellDictionaryProvider : CustomDictionaryProvider {
  private fun isHunspellPluginInstalled(): Boolean {
    val hunspellId = PluginId.getId("hunspell")
    val ideaPluginDescriptor = PluginManagerCore.getPlugin(hunspellId)
    return PluginManagerCore.isPluginInstalled(hunspellId) && ideaPluginDescriptor != null && ideaPluginDescriptor.isEnabled
  }

  override fun get(dicPath: String): Dictionary? {
    try {
      if (isIncompleteHunspell(dicPath)) {
        val hunspellBundle = HunspellDictionary.getHunspellBundle(dicPath)
        SpellCheckingNotifier.showWarningNotificationBalloon(
          SpellCheckerBundle.message("dictionary.hunspell.incomplete.title"),
          SpellCheckerBundle.message("dictionary.hunspell.incomplete", hunspellBundle.dic.toPath().fileName, hunspellBundle.aff.toPath().fileName)
        )
        return null
      }
      if (isHungarian(dicPath)) {
        SpellCheckingNotifier.showWarningNotificationBallonWithUrls(
          SpellCheckerBundle.message("dictionary.unsupported.language.title"),
          SpellCheckerBundle.message("dictionary.unsupported.language", dicPath)
        )
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
    catch (e: Exception) {
      SpellCheckingNotifier.showWarningNotificationBalloon(SpellCheckerBundle.message("dictionary.unknown.error.title"),
                                                           SpellCheckerBundle.message("dictionary.unknown.error", dicPath))
      LOG.warn("Error while loading dictionary", e)
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
    val hunspellBundle = HunspellDictionary.getHunspellBundle(path)
    return isHungarianAff(hunspellBundle.aff.toPath()) || isHungarianDic(hunspellBundle.dic.toPath())
  }

  private fun isIncompleteHunspell(path: String): Boolean {
    if (FileUtilRt.getExtension(path) != "dic") return false
    val hunspellBundle = HunspellDictionary.getHunspellBundle(path)
    val dic = hunspellBundle.dic.toPath()
    val aff = hunspellBundle.aff.toPath()

    if (Files.exists(dic) && !Files.exists(aff)) {
      try {
        return dic.anyLineMatches { line -> line.contains('/') }
      }
      catch (_: Exception) {
      }
    }
    return false
  }

  private fun isHungarianAff(path: Path): Boolean {
    if (!Files.exists(path)) return false
    try {
      return path.anyLineMatches {
        val args = it.split("\\s+".toRegex())
        args.size == 2 && args[0] == "LANG" && args[1] in setOf("hu", "HU", "hu_HU")
      }
    }
    catch (_: Exception) {
    }
    return false
  }

  private fun isHungarianDic(path: Path): Boolean {
    if (!Files.exists(path)) return false
    var counter = 0
    try {
      return path.anyLineMatches {
        counter += StringSearcher("ő", false, true).findAllOccurrences(it).size
        counter > 1000
      }
    }
    catch (_: Exception) {
    }
    return false
  }

  private inline fun Path.anyLineMatches(predicate: (String) -> Boolean): Boolean {
    return Files.newBufferedReader(this).useLines { lines -> lines.any(predicate) }
  }
}
