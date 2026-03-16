package com.intellij.driver.sdk.ui.components.common.editor

import com.intellij.driver.sdk.ui.center
import com.intellij.driver.sdk.ui.components.common.EditorTabsUiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.hasFocus
import com.intellij.driver.sdk.ui.remote.Component
import java.awt.Point

/**
 * Helper class that allows user to work with editor tabs.
 * @param ideaFrame is a reference to [IdeaFrameUI] - required for frame size calculations that will be used later moving operations (i.e., drag and drop)
 */
class EditorTabsManager(val ideaFrame: IdeaFrameUI) {
  private fun findAllEditorTabComponents(): List<EditorTabsUiComponent> {
    return ideaFrame.xx("//div[@class='EditorTabs']", EditorTabsUiComponent::class.java).list()
  }

  /**
   * Main method that allows to get all editor tabs in the whole ide frame space being sorted by default from LEFT to RIGHT and TOP to BOTTOM directions
   */
  fun getAllEditorTabs(editorSortDirection: EditorTabsGeometryWrapper.EditorSortDirection = EditorTabsGeometryWrapper.EditorSortDirection.LEFT_TO_RIGHT): List<EditorTabsGeometryWrapper> {
    return editorSortDirection.sorter(findAllEditorTabComponents()).map { editorTab -> EditorTabsGeometryWrapper(editorTabsUiComponent = editorTab) }
  }

  fun getAllEditorTabsThatHaveFileOpened(
    tabValue: String,
    editorSortDirection: EditorTabsGeometryWrapper.EditorSortDirection = EditorTabsGeometryWrapper.EditorSortDirection.LEFT_TO_RIGHT,
  ): List<EditorTabsGeometryWrapper> {
    return getAllEditorTabs(editorSortDirection).filter {
      it.editorTabsUiComponent.getTabs().any { tab -> tab.text.equals(tabValue, true) }
    }
  }

}

/**
 * Class that represents editor tab in whole ide frame space and allows to move/resize it within IDE space
 */
class EditorTabsGeometryWrapper(val editorTabsUiComponent: EditorTabsUiComponent) {

  val editorTabsComponent: Component get() = editorTabsUiComponent.component
  val editorOrientation: EditorOrientation get() = if (editorTabsComponent.height > editorTabsComponent.width) EditorOrientation.VERTICAL else EditorOrientation.HORIZONTAL

  fun isCurrentEditorInFocus(): Boolean {
    return editorTabsUiComponent.driver.hasFocus(editorTabsUiComponent)
  }

  fun isEditorTabCurrentlyOpened(tabName: String): Boolean {
    return editorTabsUiComponent.selectedTabInfo?.text.equals(tabName, true)
  }

  /**
   * All variables that represent different points can be shown on an ASCII rectangle representation below:
   * .___.___.___.___A___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___E___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___.___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___.___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * C___G___.___.___O___.___.___H___D
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___.___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___.___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___F___.___.___.___.
   * |   |   |   |   |   |   |   |   |
   * .___.___.___.___B___.___.___.___.
   *
   * Where:
   * rectangle itself - is actual editor component
   * A - top border center point
   * B - bottom border center point
   * C - left border center point
   * D - right border center point
   *
   * O - component center point
   *
   * E - top area center point
   * F - bottom area center point
   * G - left area center point
   * H - right area center point
   *
   * All these dots are represented in the corresponding variable below, according to their position on the rectangle.
   *
   */
  val centerPoint: Point get() = editorTabsUiComponent.center

  val topBorderCenterPoint: Point get() = Point(centerPoint.x, centerPoint.y - editorTabsComponent.height / 2)
  val bottomBorderCenterPoint: Point get() = Point(centerPoint.x, centerPoint.y + editorTabsComponent.height / 2)
  val leftBorderCenterPoint: Point get() = Point(centerPoint.x - editorTabsComponent.width / 2, centerPoint.y)
  val rightBorderCenterPoint: Point get() = Point(centerPoint.x + editorTabsComponent.width / 2, centerPoint.y)

  val topAreaCenterPoint: Point get() = Point(centerPoint.x, centerPoint.y - (3 * editorTabsComponent.height / 8))
  val bottomAreaCenterPoint: Point get() = Point(centerPoint.x, centerPoint.y + (3 * editorTabsComponent.height / 8))
  val leftAreaCenterPoint: Point get() = Point(centerPoint.x - (3 * editorTabsComponent.width / 8), centerPoint.y)
  val rightAreaCenterPoint: Point get() = Point(centerPoint.x + (3 * editorTabsComponent.width / 8), centerPoint.y)

  enum class EditorOrientation {
    HORIZONTAL, VERTICAL
  }

  enum class EditorSortDirection(val sorter: (List<EditorTabsUiComponent>) -> List<EditorTabsUiComponent>) {
    LEFT_TO_RIGHT({ it -> it.sortedBy { it.component.getLocationOnScreen().x } }),
    RIGHT_TO_LEFT({ it -> it.sortedByDescending { it.component.getLocationOnScreen().x } }),
    TOP_TO_BOTTOM({ it -> it.sortedBy { it.component.getLocationOnScreen().y } }),
    BOTTOM_TO_TOP({ it -> it.sortedByDescending { it.component.getLocationOnScreen().y } })
  }
}
