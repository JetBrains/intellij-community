// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorContextProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.popup.PopupAlignableComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

@ApiStatus.Internal
abstract class AbstractToolbarCombo : JComponent(), UiInspectorContextProvider, PopupAlignableComponent {
  var text: @Nls String? by Delegates.observable("", this::fireUpdateEvents)
  var leftIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var rightIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var textCutStrategy: TextCutStrategy = DefaultCutStrategy()

  var hoverBackground: Color? by Delegates.observable(null, this::fireUpdateEvents)
  var transparentHoverBackground: Color? by Delegates.observable(null, this::fireUpdateEvents)
  var highlightBackground: Color? by Delegates.observable(null, this::fireUpdateEvents)

  var betweenIconsGap: Int by Delegates.observable(0, this::fireUpdateEvents)
  var iconTextGap: Int by Delegates.observable(6, this::fireUpdateEvents)

  override fun updateUI() {
    setUI(UIManager.getUI(this))
  }

  open fun updateFromPresentation(presentation: Presentation) {
    text = presentation.text
    toolTipText = presentation.description
    leftIcons = listOfNotNull(
      if (!presentation.isEnabled) presentation.disabledIcon ?: presentation.icon
      else presentation.icon
    )
    rightIcons = listOfNotNull(presentation.getClientProperty(ActionUtil.SECONDARY_ICON))
  }

  protected fun fireUpdateEvents(prop: KProperty<*>, oldValue: Any?, newValue: Any?) {
    firePropertyChange(prop.name, oldValue, newValue)
    if (Objects.equals(oldValue, newValue)) return
    revalidate()
    repaint()
  }

  override fun getUiInspectorContext(): List<PropertyBean> {
    val result = mutableListOf<PropertyBean>()
    if (leftIcons.size == 1) {
      result.add(PropertyBean("icon", leftIcons[0], false))
    }
    if (leftIcons.size > 1) {
      result.add(PropertyBean("leftIcons", leftIcons, false))
    }

    if (rightIcons.size == 1) {
      result.add(PropertyBean("rightIcon", rightIcons[0], false))
    }
    if (rightIcons.size > 1) {
      result.add(PropertyBean("rightIcons", rightIcons, false))
    }

    return result
  }
}