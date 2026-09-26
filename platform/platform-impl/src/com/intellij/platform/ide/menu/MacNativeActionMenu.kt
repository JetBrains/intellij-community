// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.diagnostic.UILatencyLogger
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator.decorateTextIfNeeded
import com.intellij.openapi.actionSystem.impl.MenuCancelledControlFlowException
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.screenmenu.MenuItem
import java.util.concurrent.CancellationException
import javax.swing.JFrame

private const val MAX_FILL_ATTEMPTS = 3

@Throws(MenuCancelledControlFlowException::class)
internal fun createMacNativeActionMenu(context: DataContext?,
                                       place: String,
                                       group: ActionGroup,
                                       presentationFactory: PresentationFactory,
                                       isMnemonicEnabled: Boolean,
                                       frame: JFrame,
                                       useDarkIcons: Boolean): Menu {
  val groupRef = createActionRef(group)
  val presentation = presentationFactory.getPresentation(group)
  val menuPeer = Menu(decorateTextIfNeeded(group, presentation.getText(isMnemonicEnabled)))
  if (group is Toggleable && Toggleable.isSelected(presentation)) {
    menuPeer.setState(true)
  }
  // the session holds this attempt's items and the open it serves;
  // applySession decides currency on AppKit, the volatile checks here only skip wasted work
  fun fillOrRetry(session: Menu.FillSession, attempt: Int) {
    var retryScheduled = false
    try {
      WriteIntentReadAction.run {
        Utils.fillMenu(uiKind = FrameMenuUiKind(frame, session.items),
                       group = groupRef.getAction(),
                       enableMnemonics = isMnemonicEnabled,
                       presentationFactory = presentationFactory,
                       context = context ?: getDataContext(frame),
                       place = place,
                       progressPoint = null
        ) { !menuPeer.isOpened }
      }
    }
    catch (e: Throwable) {
      if (e is CancellationException || e is ControlFlowException) {
        // JBR can pump the fill event while the EDT holds the tree lock or a write action is pending.
        // fillMenu aborts in that state; retry with a regular event while the menu is still open.
        when {
          // a newer open runs its own fill chain; this one only logs
          menuPeer.openTimeNs != session.openTimeNs -> logger<Menu>().debug("menu fill is cancelled, a newer open owns the menu", e)
          menuPeer.isOpened && attempt < MAX_FILL_ATTEMPTS -> {
            retryScheduled = true
            logger<Menu>().debug("menu fill is cancelled, attempt ${attempt + 1} is scheduled", e)
            session.disposeItems()
            if (attempt == 0) {
              // keep a stub item for the open apply: AppKit closes a menu that becomes empty
              session.items.add(MenuItem())
            }
            val modality = ModalityState.current()
            ApplicationManager.getApplication().invokeLater({
              if (menuPeer.isOpened && menuPeer.openTimeNs == session.openTimeNs) {
                val retrySession = session.nextAttempt()
                try {
                  fillOrRetry(retrySession, attempt + 1)
                }
                finally {
                  menuPeer.applySession(retrySession)
                }
              }
              else {
                // the menu was closed or reopened before the retry; keep the canceled sample
                UILatencyLogger.logMainMenuLatency(session.openTimeNs)
              }
            }, modality)
          }
          menuPeer.isOpened -> logger<Menu>().warn("CancellationException/ControlFlowException is not expected", Throwable().initCause(e))
          else -> logger<Menu>().debug("menu fill is cancelled, the menu is closed", e)
        }
      }
      else {
        logger<Menu>().error(e)
      }
    }
    finally {
      // one FUS event per open: a scheduled retry continues this chain and logs later
      if (!retryScheduled) {
        UILatencyLogger.logMainMenuLatency(session.openTimeNs)
      }
    }
  }
  menuPeer.setOnOpen(frame) { session -> fillOrRetry(session, 0) }
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
  return context
}

// items is a model of a mutable JComponent with children in non-native path of Utils.fillMenu
internal class FrameMenuUiKind(val frame: JFrame, val items: MutableList<MenuItem?>) : ActionUiKind.Popup {
  override fun isMainMenu(): Boolean = true
}
