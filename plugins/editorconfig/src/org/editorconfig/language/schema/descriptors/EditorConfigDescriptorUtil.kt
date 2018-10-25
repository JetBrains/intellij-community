// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors

import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor

// Note: also returns false if descriptor is not in pair
tailrec fun EditorConfigDescriptor.isLeftInPair(): Boolean {
  val parent = this.parent
  if (parent is EditorConfigPairDescriptor) {
    return this === parent.first
  }
  if (parent == null) return false
  return parent.isLeftInPair()
}

// Note: also returns false if descriptor is not in pair
tailrec fun EditorConfigDescriptor.isRightInPair(): Boolean {
  val parent = this.parent
  if (parent is EditorConfigPairDescriptor) {
    return this === parent.second
  }
  if (parent == null) return false
  return parent.isRightInPair()
}

fun collectDescriptorMappings(
  childElement: EditorConfigDescribableElement,
  parentElement: EditorConfigDescribableElement
): Map<EditorConfigDescriptor, EditorConfigDescribableElement?> {
  val result = mutableMapOf<EditorConfigDescriptor, EditorConfigDescribableElement>()

  fun save(element: EditorConfigDescribableElement) {
    val descriptor = element.getDescriptor(true) ?: return
    result[descriptor] = element
  }

  var current: EditorConfigDescribableElement? = childElement
  while (current != null && current != parentElement.parent) {
    save(current)
    current = current.describableParent
  }

  return result
}
