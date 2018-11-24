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
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.groovy.GroovyBundle.message

class GroovySelectModuleStep(
  modules: List<Module>,
  titleProvider: (Module) -> String,
  private val consumer: (Module) -> Unit
) : BaseListPopupStep<Module>(message("select.module.description"), modules, PlatformIcons.CONTENT_ROOT_ICON_CLOSED) {

  private val myTitles = modules.associate { it to titleProvider(it) }

  override fun getTextFor(value: Module): String = myTitles[value] ?: value.name

  override fun getIndexedString(value: Module): String = value.name

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun onChosen(selectedValue: Module, finalChoice: Boolean): PopupStep<*>? {
    consumer(selectedValue)
    return null
  }
}
