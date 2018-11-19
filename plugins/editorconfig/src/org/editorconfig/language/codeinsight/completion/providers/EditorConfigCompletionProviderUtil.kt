// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor

object EditorConfigCompletionProviderUtil {
  fun isSimple(key: EditorConfigDescriptor): Boolean = when (key) {
    is EditorConfigConstantDescriptor -> true
    is EditorConfigUnionDescriptor -> key.children.all(::isSimple)
    else -> false
  }

  fun selectSimpleParts(descriptor: EditorConfigDescriptor, start: Char? = null): List<EditorConfigConstantDescriptor> {
    val result = mutableListOf<EditorConfigConstantDescriptor>()
    collectAllConstantDescriptors(descriptor, start, result)
    if (start != null && result.isEmpty()) collectAllConstantDescriptors(descriptor, null, result)
    return result
  }

  private fun collectAllConstantDescriptors(
    descriptor: EditorConfigDescriptor,
    start: Char?,
    result: MutableList<EditorConfigConstantDescriptor>
  ) {
    when (descriptor) {
      is EditorConfigConstantDescriptor -> if (start == null || descriptor.text.startsWith(start)) result.add(descriptor)
      is EditorConfigOptionDescriptor -> collectAllConstantDescriptors(descriptor.key, start, result)
      is EditorConfigUnionDescriptor -> descriptor.children.forEach { collectAllConstantDescriptors(it, start, result) }
      else -> {
      }
    }
  }

  fun createLookupAndCheckDeprecation(it: EditorConfigDescribableElement): LookupElementBuilder =
    LookupElementBuilder.create(it).withStrikeoutness(it.getDescriptor(false)?.deprecation != null)

  fun createLookupAndCheckDeprecation(it: EditorConfigDescriptor): LookupElementBuilder =
    LookupElementBuilder.create(it).withStrikeoutness(it.deprecation != null)
}
