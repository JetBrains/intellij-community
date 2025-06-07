// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import com.intellij.editorconfig.common.syntax.psi.*

abstract class EditorConfigIdentifierFinderVisitor : EditorConfigRecursiveVisitor() {
  final override fun visitFlatOptionKey(key: EditorConfigFlatOptionKey): Unit = collectIdentifier(key)
  final override fun visitQualifiedKeyPart(keyPart: EditorConfigQualifiedKeyPart): Unit = collectIdentifier(keyPart)
  final override fun visitOptionValueIdentifier(identifier: EditorConfigOptionValueIdentifier): Unit = collectIdentifier(identifier)
  final override fun visitHeader(header: EditorConfigHeader): Unit = Unit
  abstract fun collectIdentifier(identifier: EditorConfigDescribableElement)
}
