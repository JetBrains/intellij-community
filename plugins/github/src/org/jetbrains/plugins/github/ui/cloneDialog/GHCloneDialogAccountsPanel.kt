// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.SizedIcon
import com.intellij.ui.components.JBMenu
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import icons.GithubIcons
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

internal class GHCloneDialogAccountsPanel(
  private val loginController: GHLoginController,
  private val authenticationManager: GithubAuthenticationManager,
  private val apiRequestExecutorManager: GithubApiRequestExecutorManager,
  private val avatarLoader: CachingGithubUserAvatarLoader,
  private val imageResizer: GithubImageResizer
) : JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(1), 0)) {

  private val avatarSize = VcsCloneDialogUiSpec.Components.avatarSize
  private val defaultIcon = SizedIcon(GithubIcons.DefaultAvatar, avatarSize.get(), avatarSize.get())

  private val accountComponents = HashMap<GithubAccount, JLabel>()
  private val userDetails = HashMap<GithubAccount, Pair<GithubUser, Icon>>()

  private val popupMenuMouseAdapter = object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) = showPopupMenu()
  }

  init {
    addMouseListener(popupMenuMouseAdapter)
  }

  fun addAccount(account: GithubAccount) {
    if (accountComponents.isEmpty()) removeAll()

    val label = accountComponents.getOrPut(account, {
      JLabel().apply {
        icon = defaultIcon
        toolTipText = account.name
        isOpaque = !isOpaque
        addMouseListener(popupMenuMouseAdapter)
      }
    })
    add(label)
  }

  fun removeAccount(removedAccount: GithubAccount) {
    remove(accountComponents.remove(removedAccount))
    userDetails.remove(removedAccount)

    revalidate()
    repaint()
  }

  fun updateUserDetails(account: GithubAccount, user: GithubUser) {
    val iconsProvider = CachingGithubAvatarIconsProvider.Factory(
      avatarLoader,
      imageResizer,
      apiRequestExecutorManager.getExecutor(account)
    ).create(avatarSize, this)
    val avatar = iconsProvider.getIcon(user.avatarUrl)
    userDetails[account] = Pair(user, avatar)
    accountComponents[account]?.icon = avatar
  }

  private fun showPopupMenu() {
    // TODO: replace with custom popup action list
    val popupMenu = JBPopupMenu()
    for (account in authenticationManager.getAccounts()) {
      val accountSubmenu = JBMenu().apply {
        val pair = userDetails[account]
        if (pair == null) {
          text = account.name
          icon = scaleIcon(defaultIcon)
          add("Log in").addActionListener { loginController.reLogin(account) }
          addSeparator()
          add("Remove account").addActionListener { loginController.logout(account) }
        }
        else {
          val (user, avatar) = pair
          text = if (account.server.isGithubDotCom) account.name else ("${account.server.host}/${user.login}")
          icon = scaleIcon(avatar)
          add("Open on GitHub").addActionListener { BrowserUtil.browse(user.htmlUrl) }
          addSeparator()
          add("Log Out\u2026").addActionListener { loginController.logout(account) }
        }
      }
      popupMenu.add(accountSubmenu)
    }
    popupMenu.addSeparator()
    popupMenu.add("Add Account\u2026").addActionListener { loginController.addAccount() }
    popupMenu.show(this, 0, bounds.maxY.toInt())
  }

  private fun scaleIcon(icon: Icon): Icon {
    val scale = JBUI.scale(20).toFloat() / icon.iconWidth.toFloat()
    return IconUtil.scale(icon, null, scale)
  }
}