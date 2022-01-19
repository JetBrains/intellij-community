package com.intellij.remoteDev.ui

import com.intellij.ide.users.LocalUserSettings
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.impl.welcomeScreen.ActionPanel
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.remoteDev.util.UrlUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.*

@Suppress("LeakingThis")
@ApiStatus.Experimental
open class ConnectionPanel(private val manager: ConnectionManager) : ActionPanel(
  MigLayout("wrap 2, ins 20 20 0 0, novisualpadding, gap " + JBUI.scale(5) + ", flowy", null)) {

  private var defaultButtonArc = 0
  private val titleLabel: JBLabel
  private val userNameTextLabel: JBLabel
  private val remoteUrlTextLabel: JBLabel
  private val userNameTextField: JBTextField
  private val remoteUrlTextField: JBTextField
  private val connectActionButton: JButton
  private var connectionButtonPanel = JRootPane()

  init {
    border = BorderFactory.createEmptyBorder()
    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()

    val connectKeyAdapter = object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e != null && e.keyCode == 10) {
          connect()
        }
      }
    }

    titleLabel = JBLabel(RemoteDevUtilBundle.message("connection.panel.description"), SwingConstants.LEFT).apply {
      isOpaque = false
      font = font.deriveFont(StartupUiUtil.getLabelFont().size + JBUIScale.scale(5).toFloat()).deriveFont(Font.BOLD)
    }
    userNameTextLabel = JBLabel(RemoteDevUtilBundle.message("connection.panel.cwm.name.label")).apply {
      font = StartupUiUtil.getLabelFont().deriveFont(StartupUiUtil.getLabelFont().size + JBUIScale.scale(1).toFloat()).deriveFont(Font.BOLD)
    }
    userNameTextField = JBTextField(LocalUserSettings.userName)
    userNameTextField.minimumSize = Dimension(JBUI.scale(280), userNameTextField.minimumSize.height)
    userNameTextField.maximumSize = Dimension(JBUI.scale(280), userNameTextField.maximumSize.height)
    TextComponentHint(userNameTextField, RemoteDevUtilBundle.message("connection.panel.cwm.name.textfield"))

    remoteUrlTextLabel = JBLabel(RemoteDevUtilBundle.message("connection.panel.url.label")).apply {
      font = StartupUiUtil.getLabelFont().deriveFont(StartupUiUtil.getLabelFont().size + JBUIScale.scale(1).toFloat()).deriveFont(Font.BOLD)
    }
    remoteUrlTextField = JBTextField()
    remoteUrlTextField.minimumSize = Dimension(JBUI.scale(280), remoteUrlTextField.minimumSize.height)
    remoteUrlTextField.maximumSize = Dimension(JBUI.scale(280), remoteUrlTextField.maximumSize.height)
    remoteUrlTextField.addKeyListener(connectKeyAdapter)
    TextComponentHint(remoteUrlTextField, RemoteDevUtilBundle.message("connection.panel.url.textfield"))

    val connectAction = object: AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        connect()
      }
    }.apply {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
      putValue(Action.NAME, RemoteDevUtilBundle.message("connection.panel.connect.link"))
    }

    val backAction = object: AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        manager.close()
      }
    }.apply {
      putValue(Action.NAME, RemoteDevUtilBundle.message("connection.panel.back.link"))
    }

    connectionButtonPanel = JRootPane().apply {
      layout = BorderLayout()
      isOpaque = false
      border = BorderFactory.createEmptyBorder()
    }

    this.addPropertyChangeListener(PropertyChangeListener {
      if(it.propertyName == "ancestor"){
        if(defaultButtonArc == 0){
          defaultButtonArc = UIManager.getInt("Button.arc")
          //for the connect button corners
          UIManager.put("Button.arc", 8)
        } else {
          UIManager.put("Button.arc", defaultButtonArc)
        }
      }
    })

    connectActionButton = DialogWrapper.createJButtonForAction(connectAction, connectionButtonPanel)
    connectActionButton.addKeyListener(connectKeyAdapter)
    connectActionButton.background = background
    connectionButtonPanel.add(connectActionButton, BorderLayout.CENTER)

    if (manager.canClose) {
      val backActionButton = DialogWrapper.createJButtonForAction(backAction, connectionButtonPanel)
      backActionButton.background = background
      connectionButtonPanel.add(backActionButton, BorderLayout.WEST)
    }

    layoutComponents()
  }

  protected open fun layoutComponents() {
    add(titleLabel, "span, left, gapbottom 5")
    add(userNameTextLabel, "cell 0 1")
    add(userNameTextField, "cell 1 1, wrap")
    add(remoteUrlTextLabel, "cell 0 2")
    add(remoteUrlTextField, "cell 1 2, wrap")
    add(connectionButtonPanel, "cell 0 3, gaptop 5")
  }

  private fun connect() {
    val userName = userNameTextField.text
    val urlToConnect = remoteUrlTextField.text.trim()
    if (userName.isBlank() || urlToConnect.isBlank()) return

    val onDone = {
      userNameTextField.isEnabled = true
      remoteUrlTextField.isEnabled = true
      connectActionButton.isEnabled = true
    }

    val uri = UrlUtil.parseOrShowError(urlToConnect, manager.getProductName()) ?: return

    userNameTextField.isEnabled = false
    remoteUrlTextField.isEnabled = false
    connectActionButton.isEnabled = false

    manager.connect(userName, uri, onDone)
  }

  fun getRemoteUrl() = remoteUrlTextField.text ?: ""
  fun setRemoteUrl(url: String) {
    if (url.isNotEmpty()) {
      remoteUrlTextField.text = url
    }
  }
}