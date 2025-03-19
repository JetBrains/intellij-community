// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager

abstract class EditorConfigOptionBase(node: ASTNode) : EditorConfigDescribableElementBase(node), EditorConfigOption {
  override fun getPresentation(): ItemPresentation? {
    return PresentationData(name, declarationSite, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property), null)
  }

  final override fun getDescriptor(smart: Boolean): EditorConfigOptionDescriptor? =
    CachedValuesManager.getCachedValue(this, if (smart) SMART_VALUE_KEY else DUMB_VALUE_KEY) {
      val descriptorManager = EditorConfigOptionDescriptorManager.getInstance(project)
      val descriptor = descriptorManager.getOptionDescriptor(key, keyParts, smart)
      CachedValueProvider.Result.create(descriptor, this)
    }

  override fun getName(): String {
    return keyParts.joinToString(separator = ".")
  }

  private val key: PsiElement
    get() = flatOptionKey ?: qualifiedOptionKey ?: throw IllegalStateException()

  final override fun getKeyParts(): List<String> =
    flatOptionKey?.text?.let(::listOf)
    ?: qualifiedOptionKey?.qualifiedKeyPartList?.map(EditorConfigQualifiedKeyPart::getText)
    ?: emptyList()

  final override fun getAnyValue() =
    optionValueIdentifier
    ?: optionValueList
    ?: optionValuePair

  private companion object {
    private val SMART_VALUE_KEY = Key.create<CachedValue<EditorConfigOptionDescriptor>>("editorconfig.option.descriptor.smart")
    private val DUMB_VALUE_KEY = Key.create<CachedValue<EditorConfigOptionDescriptor>>("editorconfig.option.descriptor.dumb")
  }
}
