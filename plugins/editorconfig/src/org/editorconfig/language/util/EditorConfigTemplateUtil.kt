// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.openapi.diagnostic.logger
import org.editorconfig.language.codeinsight.completion.templates.EditorConfigTemplateBuilder
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor

object EditorConfigTemplateUtil {
  /**
   * Merges given descriptors are merged into a single-option template
   */
  fun buildTemplate(
    key: String,
    descriptors: List<EditorConfigQualifiedKeyDescriptor>,
    section: EditorConfigSection,
    substitutions: Map<String, String> = emptyMap(),
    initialNewLine: Boolean = true
  ) = EditorConfigTemplateBuilder(key, section, substitutions, initialNewLine).appendDescriptorGroup(descriptors).build()

  /**
   * Creates separate option for every given key
   */
  fun buildFullTemplate(
    key: String,
    descriptors: List<EditorConfigQualifiedKeyDescriptor>,
    section: EditorConfigSection,
    substitutions: Map<String, String> = emptyMap(),
    initialNewLine: Boolean = true
  ): TemplateImpl {
    val builder = EditorConfigTemplateBuilder(key, section, substitutions, initialNewLine)
    descriptors.map(::listOf).forEach { builder.appendDescriptorGroup(it) }
    return builder.build()
  }

  fun buildSameStartClasses(descriptors: List<EditorConfigQualifiedKeyDescriptor>): Map<String, List<EditorConfigQualifiedKeyDescriptor>> {
    val classes: MutableMap<String, MutableList<EditorConfigQualifiedKeyDescriptor>> = hashMapOf()

    fun add(text: String, descriptor: EditorConfigQualifiedKeyDescriptor) {
      val destination = classes[text]
      if (destination != null) destination.add(descriptor)
      else classes[text] = mutableListOf(descriptor)
    }

    fun add(descriptor: EditorConfigQualifiedKeyDescriptor, first: EditorConfigDescriptor): Unit = when (first) {
      is EditorConfigConstantDescriptor -> add(first.text, descriptor)
      is EditorConfigUnionDescriptor -> first.children.forEach { add(descriptor, it) }
      else -> throw IllegalStateException()
    }

    descriptors.forEach { add(it, it.children.first()) }
    return classes
  }

  fun checkStructuralConsistency(descriptor: EditorConfigQualifiedKeyDescriptor): Boolean {
    val result = descriptor.children.asSequence().map(DescriptorType.Companion::from).none {
      it is DescriptorType.Inconsistent
    }

    if (!result) {
      Log.warn("found structurally inconsistent descriptor")
      Log.warn(descriptor.toString())
    }
    return result
  }

  fun checkStructuralEquality(descriptors: List<EditorConfigQualifiedKeyDescriptor>): Boolean {
    val result = descriptors.asSequence().zipWithNext().all { (prev, current) -> haveStructuralEquality(prev, current) }
    if (!result) {
      Log.warn("found descriptors that have same start but differ structurally")
      Log.warn(descriptors.toString())
    }
    return result
  }

  private fun haveStructuralEquality(first: EditorConfigQualifiedKeyDescriptor, second: EditorConfigQualifiedKeyDescriptor): Boolean {
    if (first.children.size != second.children.size) return false
    return first.children.map(DescriptorType.Companion::from)
      .zip(second.children.map(DescriptorType.Companion::from))
      .all { (first, second) -> first == second }
  }

  fun startsWithVariable(descriptor: EditorConfigQualifiedKeyDescriptor) =
    DescriptorType.from(descriptor.children.first()) is DescriptorType.Variable

  private sealed class DescriptorType {
    object Constant : DescriptorType()
    object Inconsistent : DescriptorType()
    data class Variable(val id: String) : DescriptorType()

    companion object {
      fun from(descriptor: EditorConfigDescriptor): DescriptorType = when (descriptor) {
        is EditorConfigConstantDescriptor -> DescriptorType.Constant
        is EditorConfigDeclarationDescriptor -> DescriptorType.Variable(descriptor.id)
        is EditorConfigUnionDescriptor -> {
          val types = descriptor.children.map { from(it) }
          if (types.all { it is DescriptorType.Constant }) DescriptorType.Constant
          else {
            val variables = types.mapNotNull { it as? DescriptorType.Variable }
            if (variables.size == types.size && variables.isNotEmpty()) {
              val id = variables[0].id
              if (variables.all { it.id == id }) DescriptorType.Variable(id)
            }

            DescriptorType.Inconsistent
          }
        }
        else -> throw IllegalStateException()
      }
    }
  }

  private val Log = logger<EditorConfigTemplateUtil>()
  private var idCounter = 0
  val uniqueId get() = (++idCounter).toString()
}
