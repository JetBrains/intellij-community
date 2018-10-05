// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.icons.AllIcons
import org.editorconfig.language.codeinsight.linemarker.EditorConfigSectionLineMarkerProviderUtil.createActualChildFilter
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.isSubcaseOf
import javax.swing.Icon

class EditorConfigOverriddenHeaderFinder : EditorConfigHeaderLineMarkerProviderBase() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = EditorConfigPsiTreeUtil.findAllChildrenFiles(file) + file
  override fun createRelevantHeaderFilter(header: EditorConfigHeader) = createActualChildFilter(header)
  override fun createMatchingHeaderFilter(header: EditorConfigHeader): (EditorConfigHeader) -> Boolean = {
    it.isSubcaseOf(header)
  }

  override val navigationTitleKey = "message.header.overridden.title"
  override val findUsagesTitleKey = "message.header.overridden.find-usages-title"
  override val tooltipKeySingular = "message.header.overridden.element"
  override val tooltipKeyPlural = "message.header.overridden.multiple"
  override val icon: Icon = AllIcons.General.OverridenMethod
}
