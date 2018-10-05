// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.icons.AllIcons
import org.editorconfig.language.codeinsight.linemarker.EditorConfigSectionLineMarkerProviderUtil.createActualParentFilter
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.isSubcaseOf
import javax.swing.Icon

class EditorConfigOverridingHeaderFinder : EditorConfigHeaderLineMarkerProviderBase() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = EditorConfigPsiTreeUtil.findAllParentsFiles(file)
  override fun createRelevantHeaderFilter(header: EditorConfigHeader) = createActualParentFilter(header)
  override fun createMatchingHeaderFilter(header: EditorConfigHeader): (EditorConfigHeader) -> Boolean = {
    header.isSubcaseOf(it)
  }

  override val navigationTitleKey = "message.header.overriding.title"
  override val findUsagesTitleKey = "message.header.overriding.find-usages-title"
  override val tooltipKeySingular = "message.header.overriding.element"
  override val tooltipKeyPlural = "message.header.overriding.multiple"
  override val icon: Icon = AllIcons.General.OverridingMethod
}
