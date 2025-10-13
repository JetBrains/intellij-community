package com.intellij.grazie.ide.ui.widget

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import com.intellij.ui.popup.AbstractPopup
import java.awt.Point
import javax.swing.JComponent

internal fun Panel.panel(visibleIf: ComponentPredicate, block: Panel.() -> Unit): Panel = panel(block).visibleIf(visibleIf)

internal fun Panel.either(isLeft: ComponentPredicate, left: Panel.() -> Unit, right: Panel.() -> Unit): Panel =
  panel {
    panel(left).visibleIf(isLeft)
    panel(right).visibleIf(!isLeft)
  }

internal fun Panel.rowGroup(init: Panel.() -> Unit): Row = row { panel { init(this) } }

internal fun Row.panel(visibleIf: ComponentPredicate, block: Panel.() -> Unit): Panel = panel(init = block).visibleIf(visibleIf)

internal fun Panel.row(visibleIf: ComponentPredicate, block: Row.() -> Unit): Row = row(init = block).visibleIf(visibleIf)

internal fun JBPopup.adjustLocation(component: JComponent, verticalOffset: Int = 0, horizontalOffset: Int = 0) {
  val point = RelativePoint(component, Point())
  val adComponentHeight = (this as? AbstractPopup)?.adComponentHeight ?: 0
  val location = Point(point.screenPoint).apply {
    x -= size.width - component.width - horizontalOffset
    y -= size.height + adComponentHeight + verticalOffset
  }
  setLocation(location)
}

internal fun JBPopup.showAboveOnTheLeft(component: JComponent, verticalOffset: Int = 0, horizontalOffset: Int = 0) {
  addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      val popup = event.asPopup()
      popup.adjustLocation(component, verticalOffset, horizontalOffset)
    }
  })
  show(component)
}
