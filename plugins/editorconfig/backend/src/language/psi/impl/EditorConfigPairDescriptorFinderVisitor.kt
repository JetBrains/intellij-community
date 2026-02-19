// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor

class EditorConfigPairDescriptorFinderVisitor : EditorConfigDescriptorVisitor {
  var descriptor: EditorConfigPairDescriptor? = null

  override fun visitOption(option: EditorConfigOptionDescriptor): Unit =
    option.value.accept(this)

  override fun visitUnion(union: EditorConfigUnionDescriptor) {
    union.children.forEach {
      it.accept(this)
      if (descriptor != null) return
    }
  }

  override fun visitPair(pair: EditorConfigPairDescriptor) {
    // Note: might also need to check if values match in case there's union of pairs
    descriptor = pair
  }
}
