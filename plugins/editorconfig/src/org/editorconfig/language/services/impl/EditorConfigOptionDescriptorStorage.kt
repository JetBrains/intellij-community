// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.psi.PsiElement
import com.intellij.util.containers.CollectionFactory
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil

class EditorConfigOptionDescriptorStorage(source: Iterable<EditorConfigOptionDescriptor>) {
  val allDescriptors: List<EditorConfigOptionDescriptor>
  private val descriptors: Map<String, List<EditorConfigOptionDescriptor>>
  private val badDescriptors: List<EditorConfigOptionDescriptor>

  init {
    val allDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    val descriptors = CollectionFactory.createCaseInsensitiveStringMap<MutableList<EditorConfigOptionDescriptor>>()
    val badDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    for (optionDescriptor in source) {
      allDescriptors.add(optionDescriptor)

      val constants = EditorConfigDescriptorUtil.collectConstants(optionDescriptor.key)
      if (constants.isEmpty()) {
        badDescriptors.add(optionDescriptor)
        continue
      }

      for (constant in constants) {
        var list = descriptors[constant]
        if (list == null) {
          list = mutableListOf()
          descriptors[constant] = list
        }
        list.add(optionDescriptor)
      }
    }
    this.allDescriptors = allDescriptors
    this.descriptors = descriptors
    this.badDescriptors = badDescriptors
  }

  operator fun get(key: PsiElement, parts: List<String>): EditorConfigOptionDescriptor? {
    for (part in parts) {
      val partDescriptors = descriptors[part] ?: continue
      for (descriptor in partDescriptors) {
        if (descriptor.key.matches(key)) return descriptor
      }
    }

    return badDescriptors.firstOrNull { it.key.matches(key) }
  }
}
