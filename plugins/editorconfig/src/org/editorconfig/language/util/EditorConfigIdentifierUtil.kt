// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.util.core.EditorConfigIdentifierUtilCore
import org.editorconfig.language.util.core.EditorConfigPsiTreeUtilCore

object EditorConfigIdentifierUtil {
  fun findIdentifiers(section: EditorConfigSection, id: String? = null, text: String? = null): List<EditorConfigDescribableElement> =
    findDeclarations(section, id, text) + findReferences(section, id, text)

  fun findDeclarations(section: EditorConfigSection, id: String? = null, text: String? = null): List<EditorConfigDescribableElement> {
    val result = mutableListOf<EditorConfigDescribableElement>()

    fun process(element: EditorConfigDescribableElement) {
      if (EditorConfigIdentifierUtilCore.matchesDeclaration(element, id, text)) result.add(element)
      element.children.mapNotNull { it as? EditorConfigDescribableElement }.forEach { process(it) }
    }

    EditorConfigPsiTreeUtilCore
      .findMatchingSections(section)
      .flatMap(EditorConfigSection::getOptionList)
      .forEach(::process)

    return result
  }

  fun findReferences(section: EditorConfigSection, id: String? = null, text: String? = null): List<EditorConfigDescribableElement> {
    val result = mutableListOf<EditorConfigDescribableElement>()

    fun process(element: EditorConfigDescribableElement) {
      if (EditorConfigIdentifierUtilCore.matchesReference(element, id, text)) result.add(element)
      element.children.mapNotNull { it as? EditorConfigDescribableElement }.forEach { process(it) }
    }

    EditorConfigPsiTreeUtilCore
      .findMatchingSections(section)
      .flatMap(EditorConfigSection::getOptionList)
      .forEach(::process)

    return result
  }
}
