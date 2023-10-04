// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import com.intellij.compose.ActionGroupPopup
import com.intellij.compose.JBPopupPlacer
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.ScreenUtil
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities

private const val STEP_X_PADDING = 2

@Composable
internal fun GitComposeBranchActionsPopup(
  dataContext: DataContext,
  onClose: (isOk: Boolean) -> Unit
) {
  ActionGroupPopup(
    actionGroupId = "Git.Branch",
    placer = BranchActionsPopupPlacer(),
    place = ActionPlaces.VCS_TOOLBAR_WIDGET,
    dataContext = dataContext,
    onClose = onClose
  )
}

private class BranchActionsPopupPlacer : JBPopupPlacer {
  override fun showPopup(
    anchorComponent: Component,
    density: Density,
    anchorBounds: IntRect,
    popupToShow: JBPopup
  ) {
    val relativeX = (anchorBounds.left / density.density).toInt()
    val relativeY = (anchorBounds.top / density.density).toInt()
    val point = Point(relativeX, relativeY)
    SwingUtilities.convertPointToScreen(point, anchorComponent)

    popupToShow.show(anchorComponent, anchorComponent.locationOnScreen.x + anchorComponent.width - STEP_X_PADDING, point.y)
  }

  // partially taken from com.intellij.ui.popup.WizardPopup.show
  private fun JBPopup.show(anchorComponent: Component, aScreenX: Int, aScreenY: Int) {
    val parentBounds = anchorComponent.getBoundsInWindow()
    val targetBounds = Rectangle(Point(aScreenX, aScreenY), content.preferredSize)
    parentBounds.x += STEP_X_PADDING
    parentBounds.width -= STEP_X_PADDING * 2
    ScreenUtil.moveToFit(targetBounds, ScreenUtil.getScreenRectangle(
      parentBounds.x + parentBounds.width / 2,
      parentBounds.y + parentBounds.height / 2), null)
    if (parentBounds.intersects(targetBounds)) {
      targetBounds.x = anchorComponent.getBoundsInWindow().x - targetBounds.width - STEP_X_PADDING
    }

    showInScreenCoordinates(anchorComponent, Point(targetBounds.x, targetBounds.y))
  }

  private fun Component.getBoundsInWindow() = Rectangle(locationOnScreen, size)
}
