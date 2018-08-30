// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.psi.PsiElement
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy
import gnu.trove.THashMap
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil

class EditorConfigOptionDescriptorStorage(source: Iterable<EditorConfigOptionDescriptor>) {
  val allDescriptors: List<EditorConfigOptionDescriptor>
  private val descriptors: Map<String, List<EditorConfigOptionDescriptor>>
  private val badDescriptors: List<EditorConfigOptionDescriptor>

  init {
    val allDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    val descriptors = THashMap<String, MutableList<EditorConfigOptionDescriptor>>(CaseInsensitiveStringHashingStrategy())
    val badDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    source.forEach { optionDescriptor ->
      allDescriptors.add(optionDescriptor)
      val key = optionDescriptor.key
      val constants = EditorConfigDescriptorUtil.findConstants(key)
      if (constants.isEmpty()) badDescriptors.add(optionDescriptor)
      else constants.forEach { constant ->
        val presentList = descriptors[constant]
        if (presentList != null) presentList.add(optionDescriptor)
        else descriptors[constant] = mutableListOf(optionDescriptor)
      }
    }
    this.allDescriptors = allDescriptors
    this.descriptors = descriptors
    this.badDescriptors = badDescriptors
  }

  operator fun get(key: PsiElement, parts: List<String>) = parts.flatMapNotNull {
    descriptors[it]
  }.firstOrNull {
    it.key.matches(key)
  } ?: badDescriptors.firstOrNull {
    it.key.matches(key)
  }

  private inline fun <T, R : Any> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<R?>?): List<R> {
    return flatMapNotNullTo(ArrayList(), transform)
  }

  private inline fun <T, R : Any, C : MutableCollection<in R>> Iterable<T>.flatMapNotNullTo(
    destination: C,
    transform: (T) -> Iterable<R?>?
  ): C {
    for (element in this) {
      transform(element)?.forEach {
        it?.let(destination::add)
      }
    }
    return destination
  }

}
