// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeItemDataKeys {
  const val IS_SEMANTIC: String = "SeItemDataKeys.IsSemantic"
  const val PSI_LANGUAGE_ID: String = "SeItemDataKeys.PsiLanguage"
  const val REPORTABLE_PROVIDER_ID: String = "SeItemDataKeys.ReportableProviderId"
  const val IS_COMMAND: String = "SeItemDataKeys.IsCommand"
  const val PROVIDER_SORT_WEIGHT: String = "SeItemDataKeys.ProviderSortWeight"
}
