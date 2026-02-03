// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.DependencyDescriptor
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class PluginModuleUsageTypeProvider : UsageTypeProviderEx {

  private val CONTENT_MODULE_INCLUSION = UsageType(DevKitBundle.messagePointer("usage.type.module.in.content"))
  private val DEPENDENCY_ON_MODULE = UsageType(DevKitBundle.messagePointer("usage.type.module.in.dependencies"))

  override fun getUsageType(element: PsiElement): UsageType? {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY)
  }

  override fun getUsageType(element: PsiElement, targets: Array<out UsageTarget>): UsageType? {
    val value = element as? XmlAttributeValue ?: return null
    val singleTarget = targets.singleOrNull() as? PsiElementUsageTarget ?: return null
    if (!isPluginModuleTarget(singleTarget)) return null
    return when {
      DomUtil.findDomElement(value, ContentDescriptor::class.java) != null -> return CONTENT_MODULE_INCLUSION
      DomUtil.findDomElement(value, DependencyDescriptor::class.java) != null -> return DEPENDENCY_ON_MODULE
      else -> null
    }
  }

  private fun isPluginModuleTarget(target: PsiElementUsageTarget): Boolean {
    val targetFile = target.element?.containingFile as? XmlFile ?: return false
    return DescriptorUtil.isPluginModuleFile(targetFile)
  }

}
