// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.icons.AllIcons
import org.editorconfig.language.util.headers.EditorConfigOverriddenHeaderSearcher
import javax.swing.Icon

class EditorConfigOverriddenHeaderLineMarkerProvider : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher = EditorConfigOverriddenHeaderSearcher()
  override val navigationTitleKey = "message.header.overridden.title"
  override val findUsagesTitleKey = "message.header.overridden.find-usages-title"
  override val tooltipKeySingular = "message.header.overridden.element"
  override val tooltipKeyPlural = "message.header.overridden.multiple"
  override val icon: Icon = AllIcons.Gutter.OverridenMethod
}
