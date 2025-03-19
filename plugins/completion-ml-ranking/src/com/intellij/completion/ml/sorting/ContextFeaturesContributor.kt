// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.CompletionMLPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.completion.ml.storage.MutableLookupStorage

class ContextFeaturesContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl
    if (lookup != null) {
      val storage = MutableLookupStorage.get(lookup)
      if (storage != null) {
        MutableLookupStorage.saveAsUserData(parameters, storage)
        if (CompletionMLPolicy.isReRankingDisabled(storage.language, parameters)) {
          storage.disableReRanking()
        }
        if (storage.shouldComputeFeatures() && !storage.isContextFactorsInitialized()) {
          ContextFactorCalculator.calculateContextFactors(lookup, parameters, storage)
        }
      }
    }
    super.fillCompletionVariants(parameters, result)
  }
}