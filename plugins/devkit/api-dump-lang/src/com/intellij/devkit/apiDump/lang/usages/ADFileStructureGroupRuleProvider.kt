// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.usages

import com.intellij.devkit.apiDump.lang.ADLanguage
import com.intellij.devkit.apiDump.lang.icons.ADIcons
import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.navigation.NavigationItemFileStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FileStatus
import com.intellij.pom.Navigatable
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import javax.swing.Icon

internal class ADFileStructureGroupRuleProvider : FileStructureGroupRuleProvider {
  override fun getUsageGroupingRule(project: Project): UsageGroupingRule? {
    return ADClassRule()
  }
}

private class ADClassRule : SingleParentUsageGroupingRule() {
  override fun getParentGroupFor(usage: Usage, targets: Array<out UsageTarget?>): UsageGroup? {
    val psiElement = if (usage is PsiElementUsage) usage.getElement() else null
    if (psiElement == null || psiElement.getLanguage() != ADLanguage) return null

    val classDeclaration = psiElement.parentOfType<ADClassDeclaration>() ?: return null
    return ADClassDeclarationUsageGroup(classDeclaration)
  }
}

private class ADClassDeclarationUsageGroup(psi: ADClassDeclaration) : UsageGroup {
  private val pointer = psi.createSmartPointer()
  private val text = psi.classHeader.typeReference.identifierList.lastOrNull()?.text ?: "Unknown"
  private val icon = ADIcons.getIcon(psi)

  override fun getPresentableGroupText(): @NlsContexts.ListItem String =
    text

  override fun isValid(): Boolean =
    pointer.element?.isValid ?: false

  override fun compareTo(other: UsageGroup): Int =
    presentableGroupText.compareTo(other.presentableGroupText, ignoreCase = true)

  override fun getIcon(): Icon? =
    icon

  override fun getFileStatus(): FileStatus? {
    if (pointer.getProject().isDisposed()) return null
    val file = pointer.getContainingFile()
    return file?.let { NavigationItemFileStatus.get(file) }
  }

  private val navigatable
    get() = pointer.element as? Navigatable

  override fun navigate(requestFocus: Boolean) {
    navigatable?.navigate(requestFocus)
  }

  override fun canNavigate(): Boolean =
    navigatable?.canNavigate() ?: false

  override fun canNavigateToSource(): Boolean =
    navigatable?.canNavigateToSource() ?: false

  override fun equals(other: Any?): Boolean =
    this === other || other is ADClassDeclarationUsageGroup && text == other.text

  override fun hashCode(): Int = text.hashCode()
}
