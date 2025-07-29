// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.GrazieDynamic.getLangDynamicFolder
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.isAvailableLocally
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
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
      GrazieScope.coroutineScope().launch(Dispatchers.IO) {
        val newLanguages = newState.enabledLanguages.filterHunspell()
        val prevLanguages = prevState.enabledLanguages.filterHunspell()

        ProjectManager.getInstance().openProjects.forEach { project ->
          val manager = SpellCheckerManager.getInstance(project)
          newLanguages.forEach { new ->
            val dicPath = getLangDynamicFolder(new).resolve(new.hunspellRemote!!.file).toString()
            if (!manager.isDictionaryLoad(dicPath)) {
              manager.spellChecker!!.addDictionary(new.dictionary!!)
            }
          }
          prevLanguages.forEach { prev ->
            if (prev !in newLanguages) {
              val dicPath = getLangDynamicFolder(prev).resolve(prev.hunspellRemote!!.file).toString()
              manager.removeDictionary(dicPath)
            }
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