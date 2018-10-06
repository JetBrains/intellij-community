// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import icons.EditorconfigIcons
import org.editorconfig.language.util.headers.EditorConfigPartiallyOverridingHeaderSearcher
import javax.swing.Icon

class EditorConfigPartiallyOverridingHeaderFinder : EditorConfigHeaderLineMarkerProviderBase() {
  override val searcher = EditorConfigPartiallyOverridingHeaderSearcher()
  override val navigationTitleKey = "message.header.partially-overriding.title"
  override val findUsagesTitleKey = "message.header.partially-overriding.find-usages-title"
  override val tooltipKeySingular = "message.header.partially-overriding.element"
  override val tooltipKeyPlural = "message.header.partially-overriding.multiple"
  override val icon: Icon = EditorconfigIcons.PartiallyOverriding
}
