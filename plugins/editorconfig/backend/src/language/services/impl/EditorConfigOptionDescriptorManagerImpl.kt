// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.services.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.completion.providers.EditorConfigCompletionProviderUtil
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigTemplateUtil
import org.jetbrains.annotations.TestOnly
import java.lang.ref.Reference
import java.lang.ref.SoftReference

class EditorConfigOptionDescriptorManagerImpl(project: Project) : EditorConfigOptionDescriptorManager {
  private val descriptorLoader by lazy(LazyThreadSafetyMode.PUBLICATION) { EditorConfigOptionLazyDescriptorLoader(project) }
  // These structures can be very big but are vital for plugin
  private val fullySupportedDescriptors
    get() = descriptorLoader.fullySupportedDescriptors
  private val partiallySupportedDescriptors
    get() = descriptorLoader.partiallySupportedDescriptors

  // These structures are relatively small and can be stored via strong reference
  private val requiredDeclarationDescriptorsCache = mutableMapOf<String, List<EditorConfigDeclarationDescriptor>>()
  private val declarationDescriptorsCache = mutableMapOf<String, List<EditorConfigDeclarationDescriptor>>()

  private var cachedSmartQualifiedKeys: Reference<List<EditorConfigQualifiedKeyDescriptor>> = SoftReference(null)
  private var cachedDumbQualifiedKeys: Reference<List<EditorConfigQualifiedKeyDescriptor>> = SoftReference(null)

  // These structures can be very big
  private var cachedSmartSimpleKeys: Reference<List<EditorConfigDescriptor>> = SoftReference(null)
  private var cachedDumbSimpleKeys: Reference<List<EditorConfigDescriptor>> = SoftReference(null)

  // cachedAllSmartDescriptors is redundant due to storage-level caching
  private var cachedAllDumbDescriptors: Reference<List<EditorConfigOptionDescriptor>> = SoftReference(null)

  init {
    EditorConfigOptionDescriptorProvider.EP_NAME.extensionList.forEach { it.initialize(project) }
  }

  @TestOnly
  fun reloadDescriptors(project: Project) {
    descriptorLoader.reloadDescriptors(project)

    requiredDeclarationDescriptorsCache.clear()
    declarationDescriptorsCache.clear()

    cachedSmartQualifiedKeys.clear()
    cachedDumbQualifiedKeys.clear()

    cachedSmartSimpleKeys.clear()
    cachedDumbSimpleKeys.clear()

    cachedAllDumbDescriptors.clear()
  }

  override fun getOptionDescriptor(key: PsiElement, parts: List<String>, smart: Boolean): EditorConfigOptionDescriptor? {
    fullySupportedDescriptors[key, parts]?.let { return it }
    if (smart) return null
    return partiallySupportedDescriptors[key, parts]
  }

  override fun getSimpleKeyDescriptors(smart: Boolean): List<EditorConfigDescriptor> {
    val cache = if (smart) cachedSmartSimpleKeys else cachedDumbSimpleKeys
    val cached = cache.get()
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
    val cached = cachedAllDumbDescriptors.get()
    if (cached != null) return cached
    val result = fullySupportedDescriptors.allDescriptors + partiallySupportedDescriptors.allDescriptors
    cachedAllDumbDescriptors = SoftReference(result)
    return result
  }

  override fun getDeclarationDescriptors(id: String): List<EditorConfigDeclarationDescriptor> {
    val cachedResult = declarationDescriptorsCache[id]
    if (cachedResult != null) return cachedResult
    val allDescriptors = getOptionDescriptors(false)
    val declarationDescriptors = allDescriptors.flatMap { EditorConfigDescriptorUtil.collectDeclarations(it, id) }
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

  override fun getQualifiedKeyDescriptors(smart: Boolean): List<EditorConfigQualifiedKeyDescriptor> {
    val cache = if (smart) cachedSmartQualifiedKeys else cachedDumbQualifiedKeys
    cache.get()?.let { return it }

    val result = getOptionDescriptors(smart)
      .asSequence()
      .mapNotNull { it.key as? EditorConfigQualifiedKeyDescriptor }
      .filterNot(EditorConfigTemplateUtil::startsWithVariable)
      .filter(EditorConfigTemplateUtil::checkStructuralConsistency)
      .toList()
    val newCache = SoftReference(result)
    if (smart) cachedSmartQualifiedKeys = newCache else cachedDumbQualifiedKeys = newCache
    return result
  }
}
