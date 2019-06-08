// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor

import com.google.common.net.UrlEscapers
import com.intellij.CommonBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.ui.ComponentUtil
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.TextTransferable
import org.jetbrains.ide.BuiltInServerManager
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.TitledBorder

private val pathActionName: String
  get() = ActionsBundle.message(if (isMac) "action.CopySettingsPath.mac.text" else "action.CopySettingsPath.text")

class CopySettingsPathAction : AnAction(pathActionName, ActionsBundle.message("action.CopySettingsPath.description"), null), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  companion object {
    @JvmStatic
    fun createSwingActions(supplier: Supplier<Collection<String>>): List<Action> {
      return listOf(
        createSwingAction("CopySettingsPath", pathActionName) { copy(supplier.get()) },
        // disable until REST API is not able to delegate to proper IDE
        //createSwingAction(null, "Copy ${CommonBundle.settingsTitle()} Link") {
        //  copyLink(supplier, isHttp = true)
        //},
        createSwingAction(null, "Copy ${CommonBundle.settingsTitle()} Link") {
          copyLink(supplier, isHttp = false)
        }
      )
    }

    @JvmStatic
    fun createTransferable(names: Collection<String>): Transferable? {
      if (names.isEmpty()) {
        return null
      }

      val sb = StringBuilder(if (isMac) "Preferences" else "File | Settings")
      for (name in names) {
        sb.append(" | ").append(name)
      }
      return TextTransferable(sb)
    }
  }

  override fun update(event: AnActionEvent) {
    val component = event.getData(CONTEXT_COMPONENT)
    val editor = ComponentUtil.getParentOfType(SettingsEditor::class.java, component)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    var component = event.getData(CONTEXT_COMPONENT)
    if (component is JTree) {
      ComponentUtil.getParentOfType(SettingsTreeView::class.java, component)?.let { settingsTreeView ->
        settingsTreeView.createTransferable(event.inputEvent)?.let {
          CopyPasteManager.getInstance().setContents(it)
        }
        return
      }
    }

    val names = ComponentUtil.getParentOfType(SettingsEditor::class.java, component)?.pathNames ?: return
    if (names.isEmpty()) {
      return
    }

    val inner = ComponentUtil.getParentOfType(ConfigurableEditor::class.java, component)
    if (inner != null) {
      val label = getTextLabel(component)
      val path = ArrayDeque<String>()
      while (component != null && component !== inner) {
        if (component is JBTabs) {
          component.selectedInfo?.let {
            path.addFirst(it.text)
          }
        }
        if (component is JTabbedPane) {
          path.addFirst(component.getTitleAt(component.selectedIndex))
        }
        if (component is JComponent) {
          val border = component.border
          if (border is TitledBorder) {
            val title = border.title
            if (!title.isNullOrEmpty()) {
              path.addFirst(title)
            }
          }
        }
        component = component.parent
      }

      names.addAll(path)
      if (label != null) {
        names.add(label)
      }
    }

    copy(names)
  }
}

private fun copy(names: Collection<String>): Boolean {
  val transferable = CopySettingsPathAction.createTransferable(names)
  if (transferable == null) {
    return false
  }

  CopyPasteManager.getInstance().setContents(transferable)
  return true
}

private fun getTextLabel(component: Any?): String? {
  if (component is JToggleButton) {
    val text = component.text
    if (!text.isNullOrEmpty()) {
      return text
    }
  }

  // find corresponding label
  if (component is JLabel) {
    val text = component.text
    if (!text.isNullOrEmpty()) {
      return text
    }
  }
  else if (component is JComponent) {
    return getTextLabel(component.getClientProperty("labeledBy"))
  }
  return null
}

private inline fun createSwingAction(id: String?, name: String, crossinline performer: () -> Unit): Action {
  val action = object : AbstractAction(name) {
    override fun actionPerformed(event: ActionEvent) {
      performer()
    }
  }

  if (id != null) {
    ActionManager.getInstance().getKeyboardShortcut(id)?.let {
      action.putValue(Action.ACCELERATOR_KEY, it.firstKeyStroke)
    }
  }
  return action
}

private fun copyLink(supplier: Supplier<Collection<String>>, isHttp: Boolean) {
  val builder = StringBuilder()
  if (isHttp) {
    builder
      .append("http://localhost:")
      .append(BuiltInServerManager.getInstance().port)
      .append("/api")
  }
  else {
    builder.append("jetbrains://").append(PlatformUtils.getPlatformPrefix())
  }

  builder.append("/settings?name=")

  // -- is used as separator to avoid ugly URL due to percent encoding (| encoded as %7C, but - encoded as is)
  supplier.get().joinTo(builder, "--", transform = UrlEscapers.urlFormParameterEscaper()::escape)

  CopyPasteManager.getInstance().setContents(TextTransferable(builder))
}