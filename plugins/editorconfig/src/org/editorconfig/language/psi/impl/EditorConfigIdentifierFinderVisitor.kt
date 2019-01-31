// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

abstract class EditorConfigIdentifierFinderVisitor : EditorConfigRecursiveVisitor() {
  final override fun visitFlatOptionKey(key: EditorConfigFlatOptionKey) = collectIdentifier(key)
  final override fun visitQualifiedKeyPart(keyPart: EditorConfigQualifiedKeyPart) = collectIdentifier(keyPart)
  final override fun visitOptionValueIdentifier(identifier: EditorConfigOptionValueIdentifier) = collectIdentifier(identifier)
  final override fun visitHeader(header: EditorConfigHeader) = Unit
  abstract fun collectIdentifier(identifier: EditorConfigDescribableElement)
}
