// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.annotators

import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import com.intellij.editorconfig.common.highlighting.EditorConfigTextAttributeKeys
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor

internal class EditorConfigDescriptorAnnotatorVisitor(private val holder: AnnotationHolder) : EditorConfigVisitor() {

  override fun visitQualifiedKeyPart(keyPart: EditorConfigQualifiedKeyPart) {
    val descriptor = keyPart.getDescriptor(false)
    val attributesKey = if (descriptor is EditorConfigDeclarationDescriptor) {
      EditorConfigTextAttributeKeys.PROPERTY_KEY
    }
    else {
      EditorConfigTextAttributeKeys.KEY_DESCRIPTION
    }
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(keyPart).textAttributes(attributesKey).create()
  }
}
