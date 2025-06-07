// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef.menu

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.EmptyIcon
import org.cef.callback.CefMenuModel
import java.util.function.Consumer
import javax.swing.Icon

/**
 * Wrapper over JCEF menu model to show the context menu as an IJ platform popup.
 *
 * @param items - The list if the menu items
 * @param myOnSelected - The menu selection callback. Consumes the chosen menu item or null if the menu is canceled
 * @param isRoot - The flog showing if this menu step is the root or submenu.
 *                 Closing the root menu leads to calling the menu selection callback with null.
 */
internal class JBCefMenuAdapter(
  items: List<MenuItem>,
  val myOnSelected: Consumer<MenuItem?>,
  val isRoot: Boolean = true
) :
  BaseListPopupStep<JBCefMenuAdapter.MenuItem?>(null, items) {
  constructor(model: CefMenuModel, onSelected: Consumer<MenuItem?>)
    : this(convert(model), onSelected)

  override fun onChosen(selectedValue: MenuItem?, finalChoice: Boolean): PopupStep<*>? {
    if (finalChoice) {
      if (isRoot || selectedValue != null) {
        myOnSelected.accept(selectedValue)
      }
      return FINAL_CHOICE
    }
    else if (selectedValue?.type == CefMenuModel.MenuItemType.MENUITEMTYPE_SUBMENU) {
      return JBCefMenuAdapter(selectedValue.submenu!!, myOnSelected, false)
    }

    return null
  }

  override fun getIconFor(value: MenuItem?): Icon? {
    if (!hasAnyIcon.value)  {
      return null
    }

    if (value?.checked == true)
      return AllIcons.Actions.Checked

    return EmptyIcon.ICON_16
  }

  override fun getTextFor(value: MenuItem?): String {
    return value?.label ?: ""
  }

  override fun getSeparatorAbove(value: MenuItem?): ListSeparator? {
    return if (value?.hasSeparatorAbove == true) ListSeparator() else null
  }

  override fun isSelectable(value: MenuItem?): Boolean {
    return value?.enabled == true
  }

  override fun hasSubstep(selectedValue: MenuItem?): Boolean {
    return selectedValue?.type == CefMenuModel.MenuItemType.MENUITEMTYPE_SUBMENU
  }

  override fun canceled() {
    if (isRoot) {
      myOnSelected.accept(null)
    }
  }

  private val hasAnyIcon = lazy {
    values.any { it?.type in setOf(CefMenuModel.MenuItemType.MENUITEMTYPE_CHECK, CefMenuModel.MenuItemType.MENUITEMTYPE_RADIO) }
  }

  data class MenuItem(
    @NlsSafe var label: String,
    var commandId: Int,
    var hasSeparatorAbove: Boolean,

    var type: CefMenuModel.MenuItemType,
    var groupId: Int,

    var enabled: Boolean,
    var checked: Boolean,
    var visible: Boolean,

    var submenu: List<MenuItem>?,
  )

  companion object {
    private fun convert(model: CefMenuModel?): List<MenuItem> {
      if (model == null) return emptyList()

      val mutableListOf = mutableListOf<MenuItem>()
      for (i in 0 until model.count) {
        if (!model.isVisibleAt(i)) {
          continue
        }

        if (model.getTypeAt(i) == CefMenuModel.MenuItemType.MENUITEMTYPE_SEPARATOR) {
          if (mutableListOf.isNotEmpty()) {
            mutableListOf.last().hasSeparatorAbove = true
          }
        }
        else if (model.getTypeAt(i) == CefMenuModel.MenuItemType.MENUITEMTYPE_SUBMENU) {
          mutableListOf.add(MenuItem(model.getLabelAt(i).replace("&", ""),
                                     model.getCommandIdAt(i),
                                     false,
                                     CefMenuModel.MenuItemType.MENUITEMTYPE_SUBMENU,
                                     model.getGroupIdAt(i),
                                     model.isEnabledAt(i),
                                     model.isCheckedAt(i),
                                     model.isVisibleAt(i),
                                     convert(model.getSubMenuAt(i))))
        }
        else {
          mutableListOf.add(MenuItem(model.getLabelAt(i).replace("&", ""),
                                     model.getCommandIdAt(i),
                                     false,
                                     model.getTypeAt(i),
                                     model.getGroupIdAt(i),
                                     model.isEnabledAt(i),
                                     model.isCheckedAt(i),
                                     model.isVisibleAt(i),
                                     convert(model.getSubMenuAt(i))))
        }
      }
      return mutableListOf
    }
  }
}