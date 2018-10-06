// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.icons.AllIcons
import org.editorconfig.language.util.headers.EditorConfigOverridingHeaderSearcher
import javax.swing.Icon

class EditorConfigOverridingHeaderLineMarkerProvider : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher = EditorConfigOverridingHeaderSearcher()
  override val navigationTitleKey = "message.header.overriding.title"
  override val findUsagesTitleKey = "message.header.overriding.find-usages-title"
  override val tooltipKeySingular = "message.header.overriding.element"
  override val tooltipKeyPlural = "message.header.overriding.multiple"
  override val icon: Icon = AllIcons.Gutter.OverridingMethod
}
