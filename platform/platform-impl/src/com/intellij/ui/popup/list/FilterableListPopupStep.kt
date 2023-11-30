package com.intellij.ui.popup.list

import com.intellij.openapi.ui.popup.ListPopupStep

/**
 * Implement this interface to start receiving events from speed search filter
 */
interface FilterableListPopupStep<T> : ListPopupStep<T> {
  fun updateFilter(f: String?)
}