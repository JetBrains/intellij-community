// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

/**
 * Container containing all necessary information for rendering TrafficLightRenderer.
 * Instance is created each time <code>ErrorStripeRenderer.getStatus</code> is called.
 */
class AnalyzerStatus(val icon: Icon, @Nls @get:Nls val title: String, @Nls @get:Nls val details: String, controllerCreator: () -> UIController) {
  /**
   * Lazy UI controller getter. Call only when you do need access to the UI details.
   */
  val controller : UIController by lazy(LazyThreadSafetyMode.NONE) { controllerCreator() }

  var showNavigation : Boolean = false
  var expandedStatus: List<StatusItem> = emptyList()
  var passes : List<PassWrapper> = emptyList()
  var analyzingType : AnalyzingType = AnalyzingType.COMPLETE
    private set
  private var textStatus: Boolean = false

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }

  fun withTextStatus(@Nls status: String): AnalyzerStatus {
    expandedStatus = Collections.singletonList(StatusItem(status))
    textStatus = true
    return this
  }

  fun withExpandedStatus(status: List<StatusItem>): AnalyzerStatus {
    expandedStatus = status
    return this
  }

  fun withAnalyzingType(type: AnalyzingType) : AnalyzerStatus {
    analyzingType = type
    return this
  }

  fun withPasses(passes: List<PassWrapper>) : AnalyzerStatus {
    this.passes = passes
    return this
  }

  fun isTextStatus() : Boolean = textStatus

  companion object {
    /**
     * Utility comparator which takes into account only valuable fields.
     * For example the whole UI controller is ignored.
     */
    @JvmStatic
    fun equals(a: AnalyzerStatus?, b: AnalyzerStatus?): Boolean {
      if (a == null && b == null) {
        return true
      }
      if (a == null || b == null) {
        return false
      }
      return a.icon == b.icon
             && a.expandedStatus == b.expandedStatus
             && a.title == b.title
             && a.details == b.details
             && a.showNavigation == b.showNavigation
             && a.passes == b.passes
    }

    /**
     * Default instance for classes that don't implement <code>ErrorStripeRenderer.getStatus</code>
     */
    @JvmStatic
    val EMPTY by lazy(LazyThreadSafetyMode.NONE) {
      AnalyzerStatus(EmptyIcon.ICON_0, "", "") { UIController.EMPTY }
    }

    @JvmStatic
    fun isEmpty(status: AnalyzerStatus) = status == EMPTY

    @JvmStatic
    @Internal
    val EmptyController = UIController.EMPTY
  }
}