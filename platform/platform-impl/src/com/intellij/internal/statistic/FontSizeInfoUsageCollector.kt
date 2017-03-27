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
package com.intellij.internal.statistic

import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.openapi.editor.colors.EditorColorsManager

/**
 * @author Konstantin Bulenkov
 */
class FontSizeInfoUsageCollector : UsagesCollector() {
  @Throws(CollectUsagesException::class)
  override fun getUsages(): Set<UsageDescriptor> {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val ui = UISettings.shadowInstance
    return setOf(
      UsageDescriptor("UI font size: ${ui.fontSize}"),
      UsageDescriptor("Editor font size: ${scheme.editorFontSize}"),
      UsageDescriptor("Console font size: ${scheme.consoleFontSize}"),
      UsageDescriptor("Presentation mode font size: ${ui.presentationModeFontSize}"),
      UsageDescriptor("Editor font name:  ${scheme.editorFontName}"),
      UsageDescriptor("Console font name: ${scheme.consoleFontName}"),
      UsageDescriptor("UI font name: ${ui.fontFace}")
    )
  }

  override fun getGroupId(): GroupDescriptor {
    return GroupDescriptor.create("Fonts")
  }
}
