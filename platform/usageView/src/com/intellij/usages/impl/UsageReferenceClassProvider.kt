package com.intellij.usages.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiReference
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import org.jetbrains.annotations.ApiStatus.Internal

interface UsageReferenceClassProvider {
  fun getReferenceClass(usage: Usage): Class<out PsiReference>?

  companion object {
    @Internal
    @JvmField
    val EP_NAME: ExtensionPointName<UsageReferenceClassProvider> = ExtensionPointName.create("com.intellij.usages.usageReferenceClassProvider")

    fun getReferenceClass(usage: Usage): Class<out PsiReference>? {
      return EP_NAME.extensions.map { it.getReferenceClass(usage) }.firstNotNullOfOrNull { it }
    }
  }
}

@Internal
class PsiElementUsageReferenceClassProvider : UsageReferenceClassProvider {
  override fun getReferenceClass(usage: Usage): Class<out PsiReference>? {
    return when (usage) {
      is PsiElementUsage -> {
        usage.referenceClass
      }
      else -> null
    }
  }
}