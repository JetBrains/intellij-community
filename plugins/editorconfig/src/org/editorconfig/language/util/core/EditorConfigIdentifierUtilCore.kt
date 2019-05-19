// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.core

import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.util.EditorConfigTextMatchingUtil.textMatchesToIgnoreCase

object EditorConfigIdentifierUtilCore {
  fun matchesDeclaration(element: EditorConfigDescribableElement, id: String?, text: String?): Boolean {
    val descriptor = element.getDescriptor(false) as? EditorConfigDeclarationDescriptor ?: return false
    if (id != null && descriptor.id != id) return false
    if (text != null && !textMatchesToIgnoreCase(element, text)) return false
    return true
  }

  fun matchesReference(element: EditorConfigDescribableElement, id: String?, text: String?): Boolean {
    val descriptor = element.getDescriptor(false) as? EditorConfigReferenceDescriptor ?: return false
    if (id != null && descriptor.id != id) return false
    if (text != null && !textMatchesToIgnoreCase(element, text)) return false
    return true
  }
}
