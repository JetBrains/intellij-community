// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.dsl

class HighlightTriggerParametersContext internal constructor() {
  var highlightBorder: Boolean = false
  var highlightInside: Boolean = false
  var usePulsation: Boolean = false
  var clearPreviousHighlights: Boolean = true
  /**
   * Whether to limit highlighting by the component's visible area.
   * Use it in the rare cases when it is convenient to look for a component "connected" with another one (like editor and its gutter).
   */
  var limitByVisibleRect: Boolean = true
}