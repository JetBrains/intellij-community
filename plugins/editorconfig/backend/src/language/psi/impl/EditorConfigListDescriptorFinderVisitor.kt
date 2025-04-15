// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigElementAwareDescriptorVisitor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigListDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor

class EditorConfigListDescriptorFinderVisitor(
  override val element: EditorConfigOptionValueList
) : EditorConfigElementAwareDescriptorVisitor() {
  var descriptor: EditorConfigDescriptor? = null

  override fun visitOption(option: EditorConfigOptionDescriptor) {
    option.value.accept(this)
  }

  override fun visitList(list: EditorConfigListDescriptor) {
    descriptor = list
  }

  override fun visitUnion(union: EditorConfigUnionDescriptor) {
    union.children.forEach {
      it.accept(this)
      if (descriptor != null) return
    }
  }
}
