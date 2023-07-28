package com.intellij.cce.interpreter

import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.metric.SuggestionsComparator

interface FeatureInvoker {
  fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session
  // Select comparator chooses at most one best suggestion
  fun getSelectSuggestionsComparator(): SuggestionsComparator = SuggestionsComparator.DEFAULT
  // Relevant comparator chooses any number of relevant siggestion
  fun getRelevantSuggestionsComparator(): SuggestionsComparator = SuggestionsComparator.DEFAULT

}
