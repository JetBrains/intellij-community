// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.diagnostic.UILatencyLogger
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
import javax.swing.JFrame

internal fun createMacNativeActionMenu(context: DataContext?,
                                       place: String,
                                       group: ActionGroup,
                                       presentationFactory: PresentationFactory,
                                       isMnemonicEnabled: Boolean,
                                       frame: JFrame,
                                       useDarkIcons: Boolean): Menu {
  val groupRef = createActionRef(group)
  val presentation = presentationFactory.getPresentation(group)
  val menuPeer = Menu(presentation.getText(isMnemonicEnabled))
  if (group is Toggleable && Toggleable.isSelected(presentation)) {
    menuPeer.setState(true)
  }
  menuPeer.setOnOpen(frame) {
    try {
      Utils.fillMenu(group = groupRef.getAction(),
                     component = frame,
                     nativePeer = menuPeer,
                     enableMnemonics = isMnemonicEnabled,
                     presentationFactory = presentationFactory,
                     context = context ?: getDataContext(frame),
                     place = place,
                     isWindowMenu = true,
                     useDarkIcons = NSDefaults.isDarkMenuBar(),
                     expire = { !menuPeer.isOpened })
    }
    catch (ignore: ProcessCanceledException) {
      logger<Menu>().warn("ProcessCanceledException is not expected")
    }
    catch (e: Throwable) {
      logger<Menu>().error(e)
    }
    finally {
      UILatencyLogger.MAIN_MENU_LATENCY.log(System.currentTimeMillis() - menuPeer.openTimeMs);
    }
  }
  menuPeer.listenPresentationChanges(presentation)

  if (!ExperimentalUI.isNewUI() && UISettings.getInstance().showIconsInMenus) {
    // JDK can't correctly paint our HiDPI icons at the system menu bar
    presentation.icon?.let { icon ->
      menuPeer.setIcon(getMenuBarIcon(icon = icon, dark = useDarkIcons))
    }
  }
  return menuPeer
}

private fun getDataContext(frame: JFrame): DataContext {
  val dataManager = DataManager.getInstance()

  @Suppress("DEPRECATION")
  var context = dataManager.getDataContext()
  if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
    context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor(frame))
  }
  return Utils.wrapDataContext(context)
}


