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
@Internal
class AnalyzerStatus(
  val icon: Icon,
  @Nls @get:Nls val title: String,
  @Nls @get:Nls val details: String,
  val controller: UIController,
) {

  @Deprecated("use primary constructor")
  constructor(icon: Icon, @Nls title: String, @Nls details: String, controllerCreator: () -> UIController) : this(icon, title, details, controllerCreator.invoke())

  var inspectionsState: InspectionsState? = null
  var showNavigation : Boolean = false
  var expandedStatus: List<StatusItem> = emptyList()
  var passes : List<PassWrapper> = emptyList()

  var analyzingType : AnalyzingType = AnalyzingType.COMPLETE
    private set
  private var textStatus: Boolean = false

  fun withState(value: InspectionsState) : AnalyzerStatus {
    inspectionsState = value
    return this
  }


  fun withNavigation(value: Boolean) : AnalyzerStatus {
    showNavigation = value
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

  /**
   * Utility comparator which takes into account only valuable fields.
   * For example the whole UI controller is ignored.
   */
  fun equalsTo(other: AnalyzerStatus): Boolean {
    return icon == other.icon
           && expandedStatus == other.expandedStatus
           && title == other.title
           && details == other.details
           && showNavigation == other.showNavigation
           && passes == other.passes
  }

  fun isEmpty(): Boolean = this == EMPTY

  companion object {
    /**
     * Default instance for classes that don't implement [com.intellij.openapi.editor.markup.ErrorStripeRenderer.getStatus]
     */
    @JvmStatic
    val EMPTY: AnalyzerStatus = AnalyzerStatus(EmptyIcon.ICON_0, "", "", UIController.EMPTY)

    @JvmStatic
    @Internal
    @Deprecated("use UIController.EMPTY")
    val EmptyController: UIController = UIController.EMPTY
  }
}