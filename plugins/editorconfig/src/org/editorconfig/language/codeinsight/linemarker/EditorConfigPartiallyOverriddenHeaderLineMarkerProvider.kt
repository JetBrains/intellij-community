// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import icons.EditorconfigIcons
import org.editorconfig.language.util.headers.EditorConfigPartiallyOverriddenHeaderSearcher
import javax.swing.Icon

class EditorConfigPartiallyOverriddenHeaderLineMarkerProvider : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher = EditorConfigPartiallyOverriddenHeaderSearcher()
  override val navigationTitleKey = "message.header.partially-overridden.title"
  override val findUsagesTitleKey = "message.header.partially-overridden.find-usages-title"
  override val tooltipKeySingular = "message.header.partially-overridden.element"
  override val tooltipKeyPlural = "message.header.partially-overridden.multiple"
  override val icon: Icon = EditorconfigIcons.PartiallyOverridden
}
