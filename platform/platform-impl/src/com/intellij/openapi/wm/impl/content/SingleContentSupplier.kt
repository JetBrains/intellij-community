// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.actionSystem.*
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Describes tabs and toolbar for the [SingleContentLayout].
 */
interface SingleContentSupplier {

  /**
   * Tabs will be copied into toolwindow header and managed by [SingleContentLayout].
   */
  fun getTabs() : JBTabs

  /**
   * Toolbar follows after the tabs.
   *
   * By default, current toolwindow content is used for [ActionToolbar.setTargetComponent].
   * Toolbars can be adjusted in [init].
   */
  @JvmDefault
  fun getToolbarActions() : ActionGroup? {
    return null
  }

  /**
   * Actions after close action.
   */
  @JvmDefault
  fun getContentActions() : List<AnAction> {
    return emptyList()
  }

  /**
   * Defines if a tab from [getTabs] can be closed.
   */
  @JvmDefault
  fun isClosable(tab: TabInfo) : Boolean {
    return false
  }

  @JvmDefault
  fun close(tab: TabInfo) {
    getTabs().removeTab(tab)
  }

  /**
   * This method is called after a single view mode is activated.
   *
   * @param mainToolbar main toolbar that can be customized, e.g. [ActionToolbar.setTargetComponent]
   * @param contentToolbar right sided toolbar with close action and others from [getContentActions]
   */
  @JvmDefault
  fun init(mainToolbar: ActionToolbar?, contentToolbar: ActionToolbar?) {
  }

  /**
   * This method is called to customize wrappers after tabs are changed or new view is set.
   *
   * @param wrapper additional empty panel between toolbar and close action where something can be put
   */
  @JvmDefault
  fun customize(wrapper: JComponent?) {
  }

  /**
   * This method is called after a single view mode was revoked.
   */
  @JvmDefault
  fun reset() {
  }

  @JvmDefault
  fun getMainToolbarPlace(): String = ActionPlaces.TOOLWINDOW_TITLE

  @JvmDefault
  fun getContentToolbarPlace(): String = ActionPlaces.TOOLWINDOW_TITLE

  @JvmDefault
  fun addSubContent(tabInfo: TabInfo, content: Content) {
  }

  @JvmDefault
  fun getSubContents(): Collection<Content> = emptyList()

  companion object {
    @JvmField
    val KEY = DataKey.create<SingleContentSupplier>("SingleContentSupplier")

    @JvmStatic
    fun removeSubContentsOfContent(content: Content, rightNow: Boolean) {
      val supplier = (content.component as? DataProvider)?.let(KEY::getData)
      if (supplier != null) {
        val removeSubContents = {
          for (subContent in supplier.getSubContents()) {
            subContent.manager?.removeContent(subContent, true)
          }
        }
        if (rightNow) {
          removeSubContents()
        }
        else SwingUtilities.invokeLater { removeSubContents() }
      }
    }
  }
}