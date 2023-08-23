/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.completion.ml.personalization

import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorsManager {
  companion object {
    fun shouldUseUserFactors(language: Language? = null) =
      ApplicationManager.getApplication().isEAP ||
      ApplicationInfo.getInstance().versionName == "PyCharm" &&
      (language == null || language.isKindOf("Python")) &&
      PluginManager.isPluginInstalled(PluginId.getId("org.jetbrains.completion.full.line"))

    fun getInstance(): UserFactorsManager = service()
  }

  fun getAllFactors(): List<UserFactor>
}
