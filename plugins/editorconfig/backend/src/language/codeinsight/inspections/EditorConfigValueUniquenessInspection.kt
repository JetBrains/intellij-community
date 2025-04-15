// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveListValueQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigListDescriptor

class EditorConfigValueUniquenessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitOptionValueList(list: EditorConfigOptionValueList) {
      val values = list.optionValueIdentifierList
      val listDescriptor = list.getDescriptor(false) as? EditorConfigListDescriptor ?: return
      if (listDescriptor.allowRepetitions) return

      val message = EditorConfigBundle["inspection.value.uniqueness.message"]
      values.groupByNotNull {
        val descriptor = it.getDescriptor(false) ?: return@groupByNotNull null
        findListChildDescriptor(descriptor)
      }.values.filter { it.size > 1 }.flatMap { it }.forEach {
        holder.registerProblem(it, message, EditorConfigRemoveListValueQuickFix())
      }
    }
  }

  private tailrec fun findListChildDescriptor(descriptor: EditorConfigDescriptor): EditorConfigDescriptor {
    val parent = descriptor.parent ?: throw IllegalStateException()
    if (parent is EditorConfigListDescriptor) return descriptor
    return findListChildDescriptor(parent)
  }

  private inline fun <T, K : Any> Iterable<T>.groupByNotNull(keySelector: (T) -> K?): Map<K, List<T>> {
    return groupByNotNullTo(LinkedHashMap(), keySelector)
  }

  private inline fun <T, K : Any, M : MutableMap<in K, MutableList<T>>> Iterable<T>.groupByNotNullTo(
    destination: M,
    keySelector: (T) -> K?
  ): M {
    for (element in this) {
      val key = keySelector(element) ?: continue
      val list = destination.getOrPut(key) { ArrayList() }
      list.add(element)
    }
    return destination
  }
}
