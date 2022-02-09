// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path

internal object GraziePlugin {
  const val id = "tanvd.grazi"

  object LanguageTool {
    const val version = "5.5"
    const val url = "https://resources.jetbrains.com/grazie/model/language-tool"
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

  val libFolder: Path
    get() = descriptor.pluginPath.resolve("lib")
}
