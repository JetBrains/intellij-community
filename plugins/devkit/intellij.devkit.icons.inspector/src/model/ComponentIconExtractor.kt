// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import java.lang.reflect.Modifier
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities

private val LOG = logger<ComponentIconExtractor>()

/** An icon found on screen, with what is needed to capture its paints and to describe it. */
internal class PickedIcon(
  /** The component that exposes [icon] (for cells: the list/tree/table). */
  val component: Component,
  /** Paints of SwingIcons into this component or any of its descendants are captured (for cells: the renderer component). */
  val captureScope: Component,
  /** The Swing icon; `null` for a Compose icon ([compose] is set then). */
  val icon: Icon?,
  val description: String,
  val summary: List<InspectorProperty>,
  /** Area to highlight, in [component] coordinates (whole component if `null`). */
  val highlightBounds: Rectangle?,
  /** Point in [component] coordinates the icon was picked at (used to re-extract cell icons on refresh). */
  val point: Point,
  /** Set when the icon was picked inside a Compose host ([component] and [captureScope] are the host then). */
  val compose: ComposePick? = null,
) {
  /** Short label for the picker highlight: component class · icon class · size. */
  val label: String
    get() {
      if (compose != null) {
        val capture = compose.capture
        val info = capture?.composePaintInfo
        return if (capture != null && info != null) {
          "Compose · ${simpleClassName(capture.descriptor)} · ${InspectorFormatting.size(info.finalWidth, info.finalHeight)} px"
        }
        else {
          "Compose · not icons API" + (compose.semantics?.contentDescription?.let { " · \"$it\"" } ?: "")
        }
      }
      val owner = simpleClassName(captureScope.takeIf { it !== component } ?: component)
      return if (icon == null) owner else "$owner · ${simpleClassName(icon)} · ${InspectorFormatting.iconSize(icon)}"
    }
}

/**
 * Finds the icon shown by a component: `ActionButton.getIcon()`, `JLabel`/`AbstractButton.getIcon()`, the renderer component
 * of the JList/JTree/JTable cell under the point, or a reflective no-arg `getIcon()`.
 */
internal object ComponentIconExtractor {
  /** How many parents of the deepest component are tried. */
  private const val MAX_PARENT_LEVELS = 4

  /** Walks up from [deepest] (with [point] in its coordinates) to the first component exposing an icon. */
  fun findIcon(deepest: Component, point: Point): PickedIcon? {
    var current: Component? = deepest
    var currentPoint = point
    var level = 0
    while (current != null && level <= MAX_PARENT_LEVELS) {
      val found = try {
        extract(current, currentPoint)
      }
      catch (e: Throwable) {
        LOG.debug("Cannot extract icon from ${current.javaClass.name}", e)
        null
      }
      if (found != null) return found
      val parent = current.parent ?: break
      currentPoint = SwingUtilities.convertPoint(current, currentPoint, parent)
      current = parent
      level++
    }
    return null
  }

  /**
   * The Compose icon under [point] (in [deepest] coordinates) if [deepest] is inside a Compose host: the icons-API icon whose
   * last captured draw (from [index]) contains the point, else an element with image semantics. `null` if [deepest] is not
   * Compose, or nothing icon-like is under the point.
   */
  fun findComposeIcon(deepest: Component, point: Point, index: ComposeIconIndex): PickedIcon? {
    val panel = ComposeIcons.composePanelOf(deepest) ?: return null
    val host = ComposeIcons.hostOf(panel)
    val (x, y) = ComposeIcons.toScene(panel, SwingUtilities.convertPoint(deepest, point, panel))
    val capture = index.find(host, x, y)
    val semantics = ComposeIcons.imageAt(panel, x, y)
    if (capture == null && semantics == null) return null
    val pick = ComposePick(host, panel, x, y, capture, semantics)
    val info = capture?.composePaintInfo
    val panelBounds = if (info != null) ComposeIcons.sceneToPanel(panel, info)
    else ComposeIcons.sceneToPanel(panel, semantics!!.left, semantics.top, semantics.right, semantics.bottom)
    val summary = ArrayList<InspectorProperty>()
    summary.add(InspectorProperty("component", className(host)))
    summary.add(InspectorProperty("compose panel", className(panel)))
    if (capture == null) summary.add(InspectorProperty("rendered by", "not the icons API (painter / legacy icon key)"))
    summary.addAll(ComposeIcons.properties(pick, capture))
    return PickedIcon(
      component = host,
      captureScope = host,
      icon = null,
      description = "Compose icon in ${pick.hostDescription}",
      summary = summary,
      highlightBounds = SwingUtilities.convertRectangle(panel, panelBounds, host),
      point = SwingUtilities.convertPoint(deepest, point, host),
      compose = pick,
    )
  }

  /** The icon of exactly [component] at [point] (component coordinates), or `null`. */
  fun extract(component: Component, point: Point): PickedIcon? {
    return when (component) {
      is ActionButton -> actionButton(component, point)
      is JList<*> -> listCell(component, point)
      is JTree -> treeCell(component, point)
      is JTable -> tableCell(component, point)
      is JLabel -> component.icon?.let { simple(component, it, point) }
      is AbstractButton -> component.icon?.let { simple(component, it, point) }
      else -> reflectiveIcon(component)?.let { simple(component, it, point) }
    }
  }

  private fun simple(component: Component, icon: Icon, point: Point): PickedIcon {
    val summary = listOf(
      InspectorProperty("component", className(component)),
      InspectorProperty("component bounds", rect(component.bounds)),
    )
    return PickedIcon(component, component, icon, simpleClassName(component), summary, null, point)
  }

  private fun actionButton(button: ActionButton, point: Point): PickedIcon? {
    val icon = button.icon ?: return null
    val action = button.action
    val presentation = button.presentation
    val actionId = runCatching { ActionManager.getInstance().getId(action) }.getOrNull()
    val place = actionPlace(button)
    val summary = listOf(
      InspectorProperty("component", className(button)),
      InspectorProperty("action id", actionId ?: "none"),
      InspectorProperty("action class", className(action)),
      InspectorProperty("place", place ?: "unknown"),
      InspectorProperty("presentation text", presentation.text),
      InspectorProperty("presentation description", presentation.description),
      InspectorProperty("enabled", presentation.isEnabled.toString()),
      InspectorProperty("presentation icon", presentation.icon?.let(::simpleClassName) ?: "none"),
      InspectorProperty("component bounds", rect(button.bounds)),
    )
    val description = "ActionButton" + (actionId?.let { " for $it" } ?: "") + (place?.let { " @ $it" } ?: "")
    return PickedIcon(button, button, icon, description, summary, null, point)
  }

  private fun actionPlace(button: ActionButton): String? {
    // there is no public getter for the place
    var clazz: Class<*>? = button.javaClass
    while (clazz != null) {
      val value = runCatching {
        clazz.getDeclaredField("myPlace").also { it.isAccessible = true }.get(button) as? String
      }.getOrNull()
      if (value != null) return value
      clazz = clazz.superclass
    }
    return null
  }

  // ---- cells (mirrors UiInspectorAction.getClickInfo) ----

  private fun listCell(list: JList<*>, point: Point): PickedIcon? {
    @Suppress("UNCHECKED_CAST")
    list as JList<Any?>
    val row = list.locationToIndex(point)
    if (row < 0) return null
    val bounds = list.getCellBounds(row, row) ?: return null
    if (!bounds.contains(point)) return null
    val value = list.model.getElementAt(row)
    val rendererComponent = list.cellRenderer?.getListCellRendererComponent(
      list, value, row, list.selectionModel.isSelectedIndex(row), list.hasFocus()) ?: return null
    return cell(list, rendererComponent, bounds, point, "List cell renderer of ${simpleClassName(list)}", listOf(
      InspectorProperty("row", row.toString()),
      InspectorProperty("list value", safeToString(value)),
      InspectorProperty("list value class", className(value)),
    ))
  }

  private fun treeCell(tree: JTree, point: Point): PickedIcon? {
    val path = tree.getClosestPathForLocation(point.x, point.y) ?: return null
    val bounds = tree.getPathBounds(path) ?: return null
    if (point.y < bounds.y || point.y >= bounds.y + bounds.height) return null
    val row = tree.getRowForPath(path)
    val value = path.lastPathComponent
    val rendererComponent = tree.cellRenderer?.getTreeCellRendererComponent(
      tree, value, tree.selectionModel.isPathSelected(path), tree.isExpanded(path), tree.model.isLeaf(value), row,
      tree.hasFocus()) ?: return null
    return cell(tree, rendererComponent, bounds, point, "Tree cell renderer of ${simpleClassName(tree)}", listOf(
      InspectorProperty("row", row.toString()),
      InspectorProperty("tree node", safeToString(value)),
      InspectorProperty("tree node class", className(value)),
    ))
  }

  private fun tableCell(table: JTable, point: Point): PickedIcon? {
    val row = table.rowAtPoint(point)
    val column = table.columnAtPoint(point)
    if (row < 0 || column < 0) return null
    val value = table.getValueAt(row, column)
    val renderer = table.getCellRenderer(row, column) ?: return null
    val rendererComponent = renderer.getTableCellRendererComponent(
      table, value, table.selectionModel.isSelectedIndex(row), table.hasFocus(), row, column) ?: return null
    val bounds = table.getCellRect(row, column, false)
    return cell(table, rendererComponent, bounds, point, "Table cell renderer of ${simpleClassName(table)}", listOf(
      InspectorProperty("row", row.toString()),
      InspectorProperty("column", column.toString()),
      InspectorProperty("cell value", safeToString(value)),
      InspectorProperty("cell value class", className(value)),
    ))
  }

  private fun cell(
    owner: Component,
    rendererComponent: Component,
    cellBounds: Rectangle,
    point: Point,
    description: String,
    extra: List<InspectorProperty>,
  ): PickedIcon? {
    rendererComponent.bounds = cellBounds
    UIUtil.layoutRecursively(rendererComponent)
    val inCell = Point(point.x - cellBounds.x, point.y - cellBounds.y)
    // the renderer may be a panel: prefer the child under the point, then walk up to the renderer root
    var holder: Component? = (rendererComponent as? Container)?.let { SwingUtilities.getDeepestComponentAt(it, inCell.x, inCell.y) }
                             ?: rendererComponent
    var icon: Icon? = null
    while (holder != null) {
      icon = componentIcon(holder)
      if (icon != null) break
      if (holder === rendererComponent) break
      holder = holder.parent
    }
    if (icon == null || holder == null) {
      // fall back to any icon-bearing component of the renderer
      holder = UIUtil.uiTraverser(rendererComponent).firstOrNull { componentIcon(it) != null } ?: return null
      icon = componentIcon(holder) ?: return null
    }
    val summary = ArrayList<InspectorProperty>()
    summary.add(InspectorProperty("component", className(owner)))
    summary.add(InspectorProperty("renderer component", className(rendererComponent)))
    if (holder !== rendererComponent) summary.add(InspectorProperty("icon holder", className(holder)))
    summary.addAll(extra)
    summary.add(InspectorProperty("cell bounds", rect(cellBounds)))
    return PickedIcon(owner, rendererComponent, icon, description, summary, cellBounds, point)
  }

  private fun componentIcon(component: Component): Icon? = when (component) {
    is JLabel -> component.icon
    is AbstractButton -> component.icon
    else -> reflectiveIcon(component)
  }

  /** A public no-arg `getIcon()` returning an [Icon], if the component has one. */
  private fun reflectiveIcon(component: Component): Icon? {
    val method = try {
      component.javaClass.getMethod("getIcon")
    }
    catch (_: NoSuchMethodException) {
      return null
    }
    if (Modifier.isStatic(method.modifiers) || !Icon::class.java.isAssignableFrom(method.returnType)) return null
    return try {
      method.invoke(component) as? Icon
    }
    catch (e: Throwable) {
      LOG.debug("getIcon() failed on ${component.javaClass.name}", e)
      null
    }
  }

  private fun rect(r: Rectangle): String = "(${r.x}, ${r.y}) ${r.width}×${r.height}"
}
