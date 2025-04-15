// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.jetbrains.kotlin.psi.KtProperty

private val COMPOSE_RESOURCE_USAGE_TYPE = UsageType { ComposeIdeBundle.message("compose.resources.usage.type") }

class ComposeResourcesUsageTypeProvider : UsageTypeProviderEx {
  override fun getUsageType(element: PsiElement, targets: Array<out UsageTarget>): UsageType? {
    val isComposeResourceProperty = (element.reference?.resolve() as? KtProperty)?.isComposeResourceProperty
    return if (isComposeResourceProperty == true) COMPOSE_RESOURCE_USAGE_TYPE else null
  }

  override fun getUsageType(element: PsiElement): UsageType? {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY)
  }
}