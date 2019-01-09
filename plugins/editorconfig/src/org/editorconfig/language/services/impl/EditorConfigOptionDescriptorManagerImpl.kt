// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.psi.PsiElement
import com.intellij.reference.SoftReference
import org.editorconfig.language.codeinsight.completion.providers.EditorConfigCompletionProviderUtil
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigTemplateUtil
import java.lang.ref.Reference

class EditorConfigOptionDescriptorManagerImpl : EditorConfigOptionDescriptorManager {
  // These structures can be very big but are vital for plugin
  private val fullySupportedDescriptors: EditorConfigOptionDescriptorStorage
  private val partiallySupportedDescriptors: EditorConfigOptionDescriptorStorage

  // These structures are relatively small and can be stored via strong reference
  private val requiredDeclarationDescriptorsCache = mutableMapOf<String, List<EditorConfigDeclarationDescriptor>>()
  private val declarationDescriptorsCache = mutableMapOf<String, List<EditorConfigDeclarationDescriptor>>()

  private val cachedSmartQualifiedKeys: List<EditorConfigQualifiedKeyDescriptor> by lazy { findQualifiedKeys(true) }
  private val cachedDumbQualifiedKeys: List<EditorConfigQualifiedKeyDescriptor> by lazy { findQualifiedKeys(false) }

  // These structures can be very big
  private var cachedSmartSimpleKeys: Reference<List<EditorConfigDescriptor>> = SoftReference(null)
  private var cachedDumbSimpleKeys: Reference<List<EditorConfigDescriptor>> = SoftReference(null)

  // cachedAllSmartDescriptors is redundant due to storage-level caching
  private var cachedAllDumbDescriptors: Reference<List<EditorConfigOptionDescriptor>> = SoftReference(null)

  init {
    val fullySupportedDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    val partiallySupportedDescriptors = mutableListOf<EditorConfigOptionDescriptor>()

    fun loadDescriptors(provider: EditorConfigOptionDescriptorProvider) {
      val requiresFullSupport = provider.requiresFullSupport()
      val loadedDescriptors = provider.getOptionDescriptors()
      val destination =
        if (requiresFullSupport) fullySupportedDescriptors
        else partiallySupportedDescriptors

      destination.addAll(loadedDescriptors)
    }

    EditorConfigOptionDescriptorProvider.EP_NAME.extensionList.forEach(::loadDescriptors)

    this.fullySupportedDescriptors = EditorConfigOptionDescriptorStorage(fullySupportedDescriptors)
    this.partiallySupportedDescriptors = EditorConfigOptionDescriptorStorage(partiallySupportedDescriptors)
  }

  override fun getOptionDescriptor(key: PsiElement, parts: List<String>, smart: Boolean): EditorConfigOptionDescriptor? {
    fullySupportedDescriptors[key, parts]?.let { return it }
    if (smart) return null
    return partiallySupportedDescriptors[key, parts]
  }

  override fun getSimpleKeyDescriptors(smart: Boolean): List<EditorConfigDescriptor> {
    val cache = if (smart) cachedSmartSimpleKeys else cachedDumbSimpleKeys
    val cached = SoftReference.dereference(cache)
    if (cached != null) return cached
    val result = getOptionDescriptors(true)
      .asSequence()
      .map(EditorConfigOptionDescriptor::key)
      .filter(EditorConfigCompletionProviderUtil::isSimple)
      .toList()
    val newCache = SoftReference(result)
    if (smart) cachedSmartSimpleKeys = newCache
    else cachedDumbSimpleKeys = newCache
    return result
  }

  private fun getOptionDescriptors(smart: Boolean): List<EditorConfigOptionDescriptor> {
    if (smart) return fullySupportedDescriptors.allDescriptors
    val cached = SoftReference.dereference(cachedAllDumbDescriptors)
    if (cached != null) return cached
    val result = fullySupportedDescriptors.allDescriptors + partiallySupportedDescriptors.allDescriptors
    cachedAllDumbDescriptors = SoftReference(result)
    return result
  }

  override fun getDeclarationDescriptors(id: String): List<EditorConfigDeclarationDescriptor> {
    val cachedResult = declarationDescriptorsCache[id]
    if (cachedResult != null) return cachedResult
    val allDescriptors = getOptionDescriptors(false)
    val declarationDescriptors = allDescriptors.flatMap { it -> EditorConfigDescriptorUtil.findDeclarations(it, id) }
    declarationDescriptorsCache[id] = declarationDescriptors
    return declarationDescriptors
  }

  override fun getRequiredDeclarationDescriptors(id: String): List<EditorConfigDeclarationDescriptor> {
    val cachedResult = requiredDeclarationDescriptorsCache[id]
    if (cachedResult != null) return cachedResult
    val declarationDescriptors = getDeclarationDescriptors(id)
    val required = declarationDescriptors.filter(EditorConfigDeclarationDescriptor::isRequired)
    requiredDeclarationDescriptorsCache[id] = required
    return required
  }

  override fun getQualifiedKeys(smart: Boolean): List<EditorConfigQualifiedKeyDescriptor> =
    if (smart) cachedSmartQualifiedKeys else cachedDumbQualifiedKeys

  private fun findQualifiedKeys(smart: Boolean) =
    getOptionDescriptors(smart)
      .asSequence()
      .mapNotNull { it.key as? EditorConfigQualifiedKeyDescriptor }
      .filterNot(EditorConfigTemplateUtil::startsWithVariable)
      .filter(EditorConfigTemplateUtil::checkStructuralConsistency)
      .toList()
}
