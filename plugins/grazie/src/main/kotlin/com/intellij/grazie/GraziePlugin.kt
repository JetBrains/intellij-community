// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.grazie.GrazieDynamic.getLangDynamicFolder
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.isAvailableLocally
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.spellchecker.SpellCheckerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GraziePlugin {
  const val id = "tanvd.grazi"

  object LanguageTool {
    const val version = "6.5.0.12"
    const val url = "https://resources.jetbrains.com/grazie/model/language-tool"
  }

  object Hunspell : GrazieStateLifecycle {
    const val version = "0.2.288"
    const val url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/ai/grazie/spell"

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      // Do not preload the hunspell /speller in test mode, so it won't slow down tests not related to the spellchecker.
      // We will still load it in tests but only when it is actually necessary.
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return
      }

      val newLanguages = newState.enabledLanguages.filterHunspell()
      val prevLanguages = prevState.enabledLanguages.filterHunspell()
      if (prevLanguages == newLanguages) return

      GrazieScope.coroutineScope().launch(Dispatchers.IO) {
        ProjectManager.getInstance().openProjects.forEach { project ->
          val manager = SpellCheckerManager.getInstance(project)

          (newLanguages - prevLanguages).forEach { manager.spellChecker!!.addDictionary(it.dictionary!!) }
          (prevLanguages - newLanguages).forEach { prev ->
            val dicPath = getLangDynamicFolder(prev).resolve(prev.hunspellRemote!!.file).toString()
            manager.removeDictionary(dicPath)
          }

          if (project.isInitialized && project.isOpen) {
            DaemonCodeAnalyzer.getInstance(project).restart()
          }
        }
      }
    }
  }

  private val descriptor: IdeaPluginDescriptor
    get() = PluginManagerCore.getPlugin(PluginId.getId(id))!!

  val group: String
    get() = GrazieBundle.message("grazie.group.name")

  val settingsPageName: String
    get() = GrazieBundle.message("grazie.settings.page.name")

  val isBundled: Boolean
    get() = descriptor.isBundled

  val classLoader: ClassLoader
    get() = descriptor.classLoader
}

private fun Collection<Lang>.filterHunspell(): Set<Lang> {
  return asSequence()
    .filter { it.hunspellRemote != null && isAvailableLocally(it) }
    .toSet()
}