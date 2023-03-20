// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.maddyhome.idea.copyright.import

import com.intellij.copyright.CopyrightManager
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler
import com.intellij.openapi.project.Project
import com.maddyhome.idea.copyright.CopyrightProfile

private class CopyrightConfigurationHandler: ConfigurationHandler {
  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val cfgMap = configuration.find("copyright") as? Map<*, *> ?: return
    val copyrightManager = CopyrightManager.getInstance(project)

    val profilesMap = cfgMap.get("profiles") as? Map<*, *> ?: emptyMap<Any, Any>()

    profilesMap.forEach { (key, value) ->
      val name = key as? String ?: return@forEach
      val profileConfig = value as? Map<*, *> ?: return@forEach

      val profile = CopyrightProfile(name)
      (profileConfig.get("notice") as? String)?.let { profile.notice = it }
      (profileConfig.get("keyword") as? String)?.let { profile.keyword = it }
      (profileConfig.get("allowReplaceRegexp") as? String)?.let { profile.allowReplaceRegexp = it }
      copyrightManager.replaceCopyright(name, profile)
    }

    (cfgMap.get("useDefault") as? String)?.let { defaultName ->
      copyrightManager.getCopyrights()
        .find { cp -> cp.name == defaultName }
        ?.let { defaultProfile -> copyrightManager.defaultCopyright = defaultProfile }
    }

    (cfgMap.get("scopes") as? Map<*, *>)?.forEach { (key, value) ->
      val scope = key as? String ?: return@forEach
      val profileName = value as? String ?: return@forEach
      copyrightManager.mapCopyright(scope, profileName)
    }
  }
}

