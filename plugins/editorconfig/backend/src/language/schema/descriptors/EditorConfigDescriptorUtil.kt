// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors

import com.intellij.editorconfig.common.syntax.psi.*
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.editorconfig.language.psi.impl.EditorConfigListDescriptorFinderVisitor
import org.editorconfig.language.psi.impl.EditorConfigPairDescriptorFinderVisitor
import org.editorconfig.language.psi.impl.EditorConfigValueIdentifierDescriptorFinderVisitor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnsetValueDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager

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
  parentElement: EditorConfigDescribableElement,
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

fun EditorConfigDescribableElement.getDescriptor(smart: Boolean): EditorConfigDescriptor? {
  return when (this) {
    is EditorConfigOption -> getDescriptor(smart)
    is EditorConfigFlatOptionKey -> option.getDescriptor(smart)?.key
    is EditorConfigQualifiedOptionKey -> getDescriptor(smart)
    is EditorConfigQualifiedKeyPart -> getDescriptor(smart)
    is EditorConfigOptionValueIdentifier -> getDescriptor(smart)
    is EditorConfigOptionValuePair -> getDescriptor(smart)
    is EditorConfigOptionValueList -> getDescriptor(smart)
    else -> null
  }
}

private val SMART_VALUE_KEY = Key.create<CachedValue<EditorConfigOptionDescriptor>>("editorconfig.option.descriptor.smart")
private val DUMB_VALUE_KEY = Key.create<CachedValue<EditorConfigOptionDescriptor>>("editorconfig.option.descriptor.dumb")

fun EditorConfigOption.getDescriptor(smart: Boolean): EditorConfigOptionDescriptor? {
  return CachedValuesManager.getCachedValue(this, if (smart) SMART_VALUE_KEY else DUMB_VALUE_KEY) {
    val key = flatOptionKey
              ?: qualifiedOptionKey
              ?: throw IllegalStateException()
    val descriptorManager = EditorConfigOptionDescriptorManager.getInstance(project)
    val descriptor = descriptorManager.getOptionDescriptor(key, keyParts, smart)
    CachedValueProvider.Result.create(descriptor, this)
  }
}

private fun EditorConfigQualifiedOptionKey.getDescriptor(smart: Boolean): EditorConfigQualifiedKeyDescriptor? {
  val parent = parent as? EditorConfigOption ?: return null
  val key = parent.getDescriptor(smart)?.key ?: return null
  return key as? EditorConfigQualifiedKeyDescriptor
}

private fun EditorConfigQualifiedKeyPart.getDescriptor(smart: Boolean): EditorConfigDescriptor? {
  val qualifiedKey = parent as EditorConfigQualifiedOptionKey
  val qualifiedKeyParts = qualifiedKey.qualifiedKeyPartList
  val parts = qualifiedKey.getDescriptor(smart)?.children ?: return null
  val index = qualifiedKeyParts.indexOf(this)
  val result = parts[index]
  return if (result.matches(this)) result else null
}

private fun EditorConfigOptionValueIdentifier.getDescriptor(smart: Boolean): EditorConfigDescriptor? {
  val parent = describableParent ?: return null
  val parentDescriptor = parent.getDescriptor(smart) ?: return null
  val visitor = EditorConfigValueIdentifierDescriptorFinderVisitor(this)
  parentDescriptor.accept(visitor)
  return visitor.descriptor
         ?: if (EditorConfigUnsetValueDescriptor.matches(this)) EditorConfigUnsetValueDescriptor else null
}

private fun EditorConfigOptionValuePair.getDescriptor(smart: Boolean): EditorConfigDescriptor? {
  val parent = describableParent ?: return null
  val parentDescriptor = parent.getDescriptor(smart) ?: return null
  val finder = EditorConfigPairDescriptorFinderVisitor()
  parentDescriptor.accept(finder)
  return finder.descriptor
}

private fun EditorConfigOptionValueList.getDescriptor(smart: Boolean): EditorConfigDescriptor? {
  val parent = describableParent ?: return null
  val parentDescriptor = parent.getDescriptor(smart) ?: return null
  val finder = EditorConfigListDescriptorFinderVisitor(this)
  parentDescriptor.accept(finder)
  return finder.descriptor
}

fun EditorConfigSection.containsKey(key: EditorConfigFlatOptionKey): Boolean {
  for (option in optionList) {
    val flatOptionKey = option.flatOptionKey
    if (flatOptionKey != null && definesSameOption(flatOptionKey, key)) {
      return true
    }
  }
  return false
}

private fun definesSameOption(thisKey: EditorConfigFlatOptionKey, otherKey: EditorConfigFlatOptionKey): Boolean {
  val thisDescriptor = thisKey.option.getDescriptor(false)
  val otherDescriptor = otherKey.option.getDescriptor(false)
  if (otherDescriptor == null && thisDescriptor == null) {
    return thisKey.textMatches(otherKey)
  }
  return thisDescriptor == otherDescriptor
}
