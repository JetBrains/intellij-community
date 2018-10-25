// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.*

class EditorConfigValueIdentifierDescriptorFinderVisitor(
  override val element: EditorConfigOptionValueIdentifier
) : EditorConfigElementAwareDescriptorVisitor() {
  var descriptor: EditorConfigDescriptor? = null

  override fun visitOption(option: EditorConfigOptionDescriptor) {
    option.value.accept(this)
  }

  override fun visitReference(reference: EditorConfigReferenceDescriptor) {
    descriptor = reference
  }

  override fun visitList(list: EditorConfigListDescriptor) {
    list.children.forEach {
      it.accept(this)
      if (descriptor != null) return
    }
  }

  override fun visitConstant(constant: EditorConfigConstantDescriptor) {
    if (constant.matches(element)) {
      descriptor = constant
    }
  }

  override fun visitString(string: EditorConfigStringDescriptor) {
    if (string.matches(element)) {
      descriptor = string
    }
  }

  override fun visitNumber(number: EditorConfigNumberDescriptor) {
    if (number.matches(element)) {
      descriptor = number
    }
  }

  override fun visitUnion(union: EditorConfigUnionDescriptor) {
    union.children.forEach {
      it.accept(this)
      if (descriptor != null) return
    }
  }
}
