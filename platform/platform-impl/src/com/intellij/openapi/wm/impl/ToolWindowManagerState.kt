// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.concurrency.ThreadingAssertions
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface ToolWindowManagerState : PersistentStateComponent<Element> {
  var layout: DesktopLayout
  val noStateLoaded: Boolean
  val oldLayout: DesktopLayout?
  var layoutToRestoreLater: DesktopLayout?
  val recentToolWindows: LinkedList<String>
  val scheduledLayout: AtomicProperty<DesktopLayout?>
  val isEditorComponentActive: Boolean
  var frame: ProjectFrameHelper?
  var moreButton: ToolWindowAnchor
  var showNames: Boolean
  val sideCustomWidth: MutableMap<ToolWindowAnchor, Int>
}

private const val LAYOUT_TO_RESTORE = "layout-to-restore"
private const val RECENT_TW_TAG = "recentWindows"
private const val MORE_BUTTON_TAG = "moreButton"
private const val SIDE_WIDTH_TAG = "sideWidth"

@ApiStatus.Internal
@State(name = "ToolWindowManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class ToolWindowManagerStateImpl : ToolWindowManagerState {
  private val isNewUi = ExperimentalUI.isNewUI()

  override var layout: DesktopLayout = DesktopLayout()
  override var noStateLoaded: Boolean = false
    private set
  override var oldLayout: DesktopLayout? = null
    private set
  override var layoutToRestoreLater: DesktopLayout? = null
  override val recentToolWindows: LinkedList<String> = LinkedList<String>()
  override val scheduledLayout: AtomicProperty<DesktopLayout?> = AtomicProperty(null)

  override val isEditorComponentActive: Boolean
    get() {
      ThreadingAssertions.assertEventDispatchThread()
      return ComponentUtil.getParentOfType(EditorsSplitters::class.java, IdeFocusManager.getGlobalInstance().focusOwner) != null
    }

  override var frame: ProjectFrameHelper? = null

  override var moreButton: ToolWindowAnchor = ToolWindowAnchor.LEFT

  override var showNames: Boolean = false

  override val sideCustomWidth: MutableMap<ToolWindowAnchor, Int> = mutableMapOf()

  override fun getState(): Element? {
    if (frame == null) {
      return null
    }

    val element = Element("state")
    // save layout of tool windows
    writeLayout(layout, element, isV2 = isNewUi)

    oldLayout?.let {
      writeLayout(it, element, isV2 = !isNewUi)
    }

    layoutToRestoreLater?.writeExternal(LAYOUT_TO_RESTORE)?.let {
      element.addContent(it)
    }

    if (recentToolWindows.isNotEmpty()) {
      val recentState = Element(RECENT_TW_TAG)
      recentToolWindows.forEach {
        recentState.addContent(Element("value").addContent(it))
      }
      element.addContent(recentState)
    }
    if (moreButton != ToolWindowAnchor.LEFT) {
      element.addContent(Element(MORE_BUTTON_TAG).setAttribute("side", moreButton.toString()))
    }
    if (showNames) {
      val sideWidth = Element(SIDE_WIDTH_TAG)
      for (info in sideCustomWidth.entries) {
        sideWidth.setAttribute(info.key.toString(), info.value.toString())
      }
      element.addContent(sideWidth)
    }
    return element
  }

  override fun loadState(state: Element) {
    var layoutIsScheduled = false
    for (element in state.children) {
      if (JDOMUtil.isEmpty(element)) {
        // make sure that layoutIsScheduled is not set if empty layout for some reason is provided
        continue
      }

      when (element.name) {
        DesktopLayout.TAG -> {
          val layout = DesktopLayout()
          layout.readExternal(element, isNewUi = false)
          if (isNewUi) {
            oldLayout = layout
          }
          else {
            scheduledLayout.set(layout)
            layoutIsScheduled = true
          }
        }
        "layoutV2" -> {
          val layout = DesktopLayout()
          layout.readExternal(element, isNewUi = true)
          if (isNewUi) {
            scheduledLayout.set(layout)
            layoutIsScheduled = true
          }
          else {
            oldLayout = layout
          }
        }
        LAYOUT_TO_RESTORE -> {
          layoutToRestoreLater = DesktopLayout().also { it.readExternal(element, isNewUi) }
        }
        RECENT_TW_TAG -> {
          recentToolWindows.clear()
          element.content.forEach {
            recentToolWindows.add(it.value)
          }
        }
        MORE_BUTTON_TAG -> {
          moreButton = ToolWindowAnchor.fromText(element.getAttributeValue("side"))
        }
        SIDE_WIDTH_TAG -> {
          showNames = true
          for (attribute in element.attributes) {
            sideCustomWidth[ToolWindowAnchor.fromText(attribute.name)] = attribute.value.toInt()
          }
        }
      }
    }

    if (!layoutIsScheduled) {
      noStateLoaded()
    }
  }

  override fun noStateLoaded() {
    noStateLoaded = true
  }

  private fun writeLayout(layout: DesktopLayout, parent: Element, isV2: Boolean) {
    parent.addContent(layout.writeExternal(if (isV2) "layoutV2" else DesktopLayout.TAG) ?: return)
  }
}