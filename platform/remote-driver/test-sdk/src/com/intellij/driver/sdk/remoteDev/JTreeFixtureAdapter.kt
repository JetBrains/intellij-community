package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.model.TreePath
import com.intellij.driver.model.TreePathToRowList
import com.intellij.driver.sdk.ui.CellRendererReader
import com.intellij.driver.sdk.ui.components.JTreeFixtureRef
import com.intellij.driver.sdk.ui.components.JTreeUiComponent
import com.intellij.driver.sdk.ui.remote.Robot
import java.awt.Point
import javax.swing.JTree

class JTreeFixtureAdapter(robot: Robot, component: BeControlComponentBase) :
  BeControlComponentBase(component.driver, component.frontendComponent, component.backendComponent),
  JTreeFixtureRef {
  val fixture = onFrontend(JTreeUiComponent::class) { byType(JTree::class.java.name) }.fixture

  override fun clickRow(row: Int): JTreeFixtureRef {
    fixture.clickRow(row)
    return this
  }

  override fun clickPath(path: String): JTreeFixtureRef {
    fixture.clickPath(path)
    return this
  }

  override fun doubleClickRow(row: Int): JTreeFixtureRef {
    fixture.doubleClickRow(row)
    return this
  }

  override fun doubleClickPath(path: String): JTreeFixtureRef {
    fixture.doubleClickPath(path)
    return this
  }

  override fun rightClickRow(row: Int): JTreeFixtureRef {
    fixture.rightClickRow(row)
    return this
  }

  override fun rightClickPath(path: String): JTreeFixtureRef {
    fixture.rightClickPath(path)
    return this
  }

  override fun expandRow(row: Int): JTreeFixtureRef {
    fixture.expandRow(row)
    return this
  }

  override fun collapseRow(row: Int): JTreeFixtureRef {
    fixture.collapseRow(row)
    return this
  }

  override fun expandPath(path: String): JTreeFixtureRef {
    fixture.expandPath(path)
    return this
  }

  override fun collapsePath(path: String): JTreeFixtureRef {
    fixture.collapsePath(path)
    return this
  }

  override fun separator(): String {
    return fixture.separator()
  }

  override fun valueAt(row: Int): String {
    return fixture.valueAt(row)
  }

  override fun valueAt(path: String): String {
    return fixture.valueAt(path)
  }

  override fun collectExpandedPaths(): TreePathToRowList {
    return fixture.collectExpandedPaths()
  }

  override fun collectSelectedPaths(): List<TreePath> {
    return fixture.collectSelectedPaths()
  }

  override fun selectRow(row: Int): JTreeFixtureRef? {
    return fixture.selectRow(row)
  }

  override fun expandAll(timeoutMs: Int) {
    fixture.expandAll(timeoutMs)
  }

  override fun getRowPoint(row: Int): Point {
    return fixture.getRowPoint(row)
  }

  override fun replaceCellRendererReader(reader: CellRendererReader) {
    fixture.replaceCellRendererReader(reader)
  }
}