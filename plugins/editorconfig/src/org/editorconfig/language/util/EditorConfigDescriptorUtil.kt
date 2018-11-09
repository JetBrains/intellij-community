// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast

object EditorConfigDescriptorUtil {
  fun findDeclarations(descriptor: EditorConfigDescriptor, id: String): List<EditorConfigDeclarationDescriptor> {
    val result = mutableListOf<EditorConfigDeclarationDescriptor>()

    fun process(descriptor: EditorConfigDescriptor) {
      if (descriptor is EditorConfigDeclarationDescriptor) {
        if (descriptor.id == id) {
          result.add(descriptor)
        }
      }
      descriptor.children.forEach(::process)
    }

    process(descriptor)
    return result
  }

  inline fun <reified T : EditorConfigDescriptor> getParentOfType(descriptor: EditorConfigDescriptor) =
    getParentOfType(descriptor, T::class)

  tailrec fun <T : EditorConfigDescriptor> getParentOfType(descriptor: EditorConfigDescriptor, cls: KClass<T>): T? {
    val casted = cls.safeCast(descriptor)
    if (casted != null) return casted
    val parent = descriptor.parent ?: return null
    return getParentOfType(parent, cls)
  }

  fun isConstant(descriptor: EditorConfigDescriptor): Boolean = when (descriptor) {
    is EditorConfigConstantDescriptor -> true
    is EditorConfigUnionDescriptor -> descriptor.children.all(::isConstant)
    else -> false
  }

  fun isVariable(descriptor: EditorConfigDescriptor): Boolean = when (descriptor) {
    is EditorConfigDeclarationDescriptor -> true
    is EditorConfigUnionDescriptor -> descriptor.children.all(::isVariable)
    else -> false
  }

  fun findConstants(descriptor: EditorConfigDescriptor): List<String> {
    val result = mutableListOf<String>()

    fun collectConstants(descriptor: EditorConfigDescriptor) {
      when (descriptor) {
        is EditorConfigConstantDescriptor -> result.add(descriptor.text)
        else -> descriptor.children.forEach { collectConstants(it) }
      }
    }

    collectConstants(descriptor)
    return result
  }
}
