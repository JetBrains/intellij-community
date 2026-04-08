// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object CommentUsageFilteringRule : UsageFilteringRule {
  override fun getActionId(): String = "UsageFiltering.Comments"

  override fun isVisible(usage: Usage, targets: Array<out UsageTarget>): Boolean {
    // Prefer the usage type when available (e.g. for non-PSI usages that are still classified).
    val usageType = (usage as? UsageWithType)?.usageType
    if (usageType == UsageType.COMMENT_USAGE) return false

    val element = (usage as? PsiElementUsage)?.element ?: return true
    return PsiTreeUtil.getParentOfType(element, PsiComment::class.java, /* strict = */ false) == null
  }
}

