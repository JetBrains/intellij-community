// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor

import com.intellij.idea.ActionsBundle.message
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.ui.TextTransferable
import com.intellij.util.ui.UIUtil.getParentOfType
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.TitledBorder

class CopySettingsPathAction : AnAction(name, message("action.CopySettingsPath.description"), null), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  companion object {
    private val name: String
      get() = message(if (isMac) "action.CopySettingsPath.mac.text" else "action.CopySettingsPath.text")

    @JvmStatic
    fun createSwingAction(supplier: Supplier<Transferable>): Action {
      val action = object : AbstractAction(name) {
        override fun actionPerformed(event: ActionEvent) {
          copy(supplier.get())
        }
      }

      ActionManager.getInstance().getKeyboardShortcut("CopySettingsPath")?.let {
        action.putValue(Action.ACCELERATOR_KEY, it.firstKeyStroke)
      }
      return action
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
      return TextTransferable(sb.toString())
    }
  }

  override fun update(event: AnActionEvent) {
    val component = event.getData(CONTEXT_COMPONENT)
    val editor = getParentOfType(SettingsEditor::class.java, component)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    var component = event.getData(CONTEXT_COMPONENT)
    if (component is JTree) {
      getParentOfType(SettingsTreeView::class.java, component)?.let {
        copy(it.createTransferable(event.inputEvent))
        return
      }
    }

    val names = getParentOfType(SettingsEditor::class.java, component)?.pathNames ?: return
    if (names.isEmpty()) {
      return
    }

    val inner = getParentOfType(ConfigurableEditor::class.java, component)
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

    copy(createTransferable(names))
  }
}

private fun copy(transferable: Transferable?): Boolean {
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