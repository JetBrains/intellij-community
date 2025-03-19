// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.util.TextRange
import com.intellij.ui.speedSearch.SpeedSearchSupply
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Speed search which redirects to [mainSpeedSearch], but also highlight matches from the query in [externalSpeedSearch].
 */
@ApiStatus.Internal
class CombinedSpeedSearch private constructor(private val mainSpeedSearch: SpeedSearchSupply,
                                              private val externalSpeedSearch: SpeedSearchSupply) : SpeedSearchSupply() {

  constructor(component: JComponent, externalSpeedSearch: SpeedSearchSupply) :
    this(getSupply(component, true)!!, externalSpeedSearch) {
    installSupplyTo(component, true)
  }

  override fun matchingFragments(text: String): MutableIterable<TextRange>? {
    return mainSpeedSearch.matchingFragments(text) ?: externalSpeedSearch.matchingFragments(text)
  }

  override fun isPopupActive(): Boolean {
    return mainSpeedSearch.isPopupActive || externalSpeedSearch.isPopupActive
  }

  override fun addChangeListener(listener: PropertyChangeListener) {
    mainSpeedSearch.addChangeListener(listener)
    externalSpeedSearch.addChangeListener(listener)
  }

  override fun removeChangeListener(listener: PropertyChangeListener) {
    mainSpeedSearch.removeChangeListener(listener)
    externalSpeedSearch.removeChangeListener(listener)
  }

  override fun refreshSelection() {
    mainSpeedSearch.refreshSelection()
  }

  override fun findAndSelectElement(searchQuery: String) {
    mainSpeedSearch.findAndSelectElement(searchQuery)
  }
}