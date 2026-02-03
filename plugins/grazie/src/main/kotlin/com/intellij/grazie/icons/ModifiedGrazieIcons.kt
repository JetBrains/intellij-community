package com.intellij.grazie.icons

import com.intellij.grazie.icons.GrazieIcons.Stroke.Grazie
import com.intellij.icons.AllIcons
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.IconWithOverlay
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import java.awt.Shape
import java.awt.geom.Rectangle2D
import javax.swing.Icon
import javax.swing.SwingConstants

object ModifiedGrazieIcons {

  @JvmField
  val Refresh: Icon = createIconWithBadge(base = Grazie, badge = AllIcons.Actions.BuildLoadChanges)

  @JvmField
  val ForcedLocal: Icon = createIconWithBadge(base = Grazie, badge = AllIcons.General.Warning)

  private fun createIconWithBadge(base: Icon, badge: Icon): Icon {
    val badge = IconUtil.scale(badge, ancestor = null, scale = 0.5f)
    val layeredBadge = LayeredIcon(2).apply {
      setIcon(EmptyIcon.create(base), 0)
      setIcon(badge, 1, SwingConstants.SOUTH_EAST)
    }
    return object: IconWithOverlay(base, layeredBadge) {
      override fun getOverlayShape(x: Int, y: Int): Shape {
        val baseWidth = iconWidth
        val baseHeight = iconHeight
        val width = badge.iconWidth
        val height = badge.iconHeight
        return Rectangle2D.Float(x + scale * (baseWidth - width), scale * (baseHeight - height), width * scale, height * scale)
      }
    }
  }
}