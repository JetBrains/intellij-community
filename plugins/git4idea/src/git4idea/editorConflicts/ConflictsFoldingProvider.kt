// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.lang.folding.CustomFoldingProvider

class ConflictsFoldingProvider : CustomFoldingProvider() {
  override fun isCustomRegionStart(elementText: String) = elementText.startsWith("<<<<<<<")

  override fun isCustomRegionEnd(elementText: String) = elementText.startsWith(">>>>>>>")

  override fun getPlaceholderText(elementText: String) = "Conflicts"

  override fun getDescription() = "Conflicts"

  override fun getStartString() = "Conflicts"

  override fun getEndString() = "Conflicts"
}