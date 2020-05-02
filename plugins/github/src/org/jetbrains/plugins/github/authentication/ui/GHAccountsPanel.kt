// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN
import com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.progress.ProgressVisibilityManager
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

private const val LINK_TAG = "EDIT_LINK"

internal class GHAccountsPanel(private val project: Project,
                               private val executorFactory: GithubApiRequestExecutor.Factory,
                               private val avatarLoader: CachingGithubUserAvatarLoader,
                               private val imageResizer: GithubImageResizer) : BorderLayoutPanel(), Disposable {

  private val accountListModel = CollectionListModel<GithubAccountDecorator>().apply {
    // disable link handler when there are no errors
    addListDataListener(object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent?) = setLinkHandlerEnabled(items.any { it.errorText != null })
      override fun intervalRemoved(e: ListDataEvent?) {}
      override fun intervalAdded(e: ListDataEvent?) {}
    })
  }
  private val accountList = JBList<GithubAccountDecorator>(accountListModel).apply {
    val decoratorRenderer = GithubAccountDecoratorRenderer()
    cellRenderer = decoratorRenderer
    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(decoratorRenderer))

    selectionMode = ListSelectionModel.SINGLE_SELECTION
    emptyText.apply {
      appendText(GithubBundle.message("accounts.none.added"))
      appendSecondaryText(GithubBundle.message("accounts.add"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, { addAccount() })
      appendSecondaryText(" (${KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew())})", StatusText.DEFAULT_ATTRIBUTES, null)
    }
  }

  private val progressManager = createListProgressManager()
  private val errorLinkHandler = createLinkActivationListener()
  private var errorLinkHandlerInstalled = false
  private var currentTokensMap = mapOf<GithubAccount, String?>()
  private val newTokensMap = mutableMapOf<GithubAccount, String>()

  init {
    addToCenter(ToolbarDecorator.createDecorator(accountList)
                  .disableUpDownActions()
                  .setAddAction { addAccount() }
                  .addExtraAction(object : ToolbarDecorator.ElementActionButton(GithubBundle.message("accounts.set.default"),
                                                                                AllIcons.Actions.Checked) {
                    override fun actionPerformed(e: AnActionEvent) {
                      if (accountList.selectedValue.projectDefault) return
                      for (accountData in accountListModel.items) {
                        if (accountData == accountList.selectedValue) {
                          accountData.projectDefault = true
                          accountListModel.contentsChanged(accountData)
                        }
                        else if (accountData.projectDefault) {
                          accountData.projectDefault = false
                          accountListModel.contentsChanged(accountData)
                        }
                      }
                    }

                    override fun updateButton(e: AnActionEvent) {
                      isEnabled = isEnabled && !accountList.selectedValue.projectDefault
                    }
                  })
                  .createPanel())

    Disposer.register(this, progressManager)
  }

  private fun addAccount() {
    val dialog = GithubLoginDialog(executorFactory, project, this, ::isAccountUnique)
    if (dialog.showAndGet()) {
      val githubAccount = GithubAccountManager.createAccount(dialog.getLogin(), dialog.getServer())
      newTokensMap[githubAccount] = dialog.getToken()

      val accountData = GithubAccountDecorator(githubAccount, false)
      accountListModel.add(accountData)
      loadAccountDetails(accountData)
    }
  }

  private fun editAccount(decorator: GithubAccountDecorator) {
    val dialog = GithubLoginDialog(executorFactory, project, this).apply {
      withServer(decorator.account.server.toString(), false)
      withCredentials(decorator.account.name)
    }
    if (dialog.showAndGet()) {
      decorator.account.name = dialog.getLogin()
      newTokensMap[decorator.account] = dialog.getToken()
      loadAccountDetails(decorator)
    }
  }

  private fun isAccountUnique(login: String, server: GithubServerPath) =
    accountListModel.items.none { it.account.name == login && it.account.server == server }

  /**
   * Manages link hover and click for [GithubAccountDecoratorRenderer.loadingError]
   * Sets the proper cursor and underlines the link on hover
   *
   * @see [GithubAccountDecorator.errorText]
   * @see [GithubAccountDecorator.showReLoginLink]
   * @see [GithubAccountDecorator.errorLinkPointedAt]
   */
  private fun createLinkActivationListener() = object : MouseAdapter() {

    override fun mouseMoved(e: MouseEvent) {
      val decorator = findDecoratorWithLoginLinkAt(e.point)
      if (decorator != null) {
        UIUtil.setCursor(accountList, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      }
      else {
        UIUtil.setCursor(accountList, Cursor.getDefaultCursor())
      }

      var hasChanges = false
      for (item in accountListModel.items) {
        val isLinkPointedAt = item == decorator
        hasChanges = hasChanges || isLinkPointedAt != item.errorLinkPointedAt
        item.errorLinkPointedAt = isLinkPointedAt
      }
      if (hasChanges) accountListModel.allContentsChanged()
    }

    override fun mouseClicked(e: MouseEvent) {
      findDecoratorWithLoginLinkAt(e.point)?.run(::editAccount)
    }

    /**
     * Checks if mouse is pointed at decorator error link
     *
     * @return decorator with error link under mouse pointer or null
     */
    private fun findDecoratorWithLoginLinkAt(point: Point): GithubAccountDecorator? {
      val idx = accountList.locationToIndex(point)
      if (idx < 0) return null

      val cellBounds = accountList.getCellBounds(idx, idx)
      if (!cellBounds.contains(point)) return null

      val decorator = accountListModel.getElementAt(idx)
      if (decorator?.errorText == null) return null

      val rendererComponent = accountList.cellRenderer.getListCellRendererComponent(accountList, decorator, idx, true, true)
      rendererComponent.setBounds(cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height)
      UIUtil.layoutRecursively(rendererComponent)

      val rendererRelativeX = point.x - cellBounds.x
      val rendererRelativeY = point.y - cellBounds.y
      val childComponent = UIUtil.getDeepestComponentAt(rendererComponent, rendererRelativeX, rendererRelativeY)
      if (childComponent !is SimpleColoredComponent) return null

      val childRelativeX = rendererRelativeX - childComponent.parent.x - childComponent.x
      return if (childComponent.getFragmentTagAt(childRelativeX) == LINK_TAG) decorator else null
    }
  }

  private fun setLinkHandlerEnabled(enabled: Boolean) {
    if (enabled) {
      if (!errorLinkHandlerInstalled) {
        accountList.addMouseListener(errorLinkHandler)
        accountList.addMouseMotionListener(errorLinkHandler)
        errorLinkHandlerInstalled = true
      }
    }
    else if (errorLinkHandlerInstalled) {
      accountList.removeMouseListener(errorLinkHandler)
      accountList.removeMouseMotionListener(errorLinkHandler)
      errorLinkHandlerInstalled = false
    }
  }

  fun loadExistingAccountsDetails() {
    for (accountData in accountListModel.items) {
      loadAccountDetails(accountData)
    }
  }

  private fun loadAccountDetails(accountData: GithubAccountDecorator) {
    val account = accountData.account
    val token = newTokensMap[account] ?: currentTokensMap[account]
    if (token == null) {
      accountListModel.contentsChanged(accountData.apply {
        errorText = GithubBundle.message("account.token.missing")
        showReLoginLink = true
      })
      return
    }
    val executor = executorFactory.create(token)
    progressManager.run(object : Task.Backgroundable(project, "Not Visible") {
      lateinit var loadedDetails: GithubAuthenticatedUser
      var correctScopes: Boolean = true

      override fun run(indicator: ProgressIndicator) {
        val (details, scopes) = GHSecurityUtil.loadCurrentUserWithScopes(executor, indicator, account.server)
        loadedDetails = details
        correctScopes = GHSecurityUtil.isEnoughScopes(scopes.orEmpty())
      }

      override fun onSuccess() {
        accountListModel.contentsChanged(accountData.apply {
          details = loadedDetails
          iconProvider = CachingGithubAvatarIconsProvider(avatarLoader, imageResizer, executor, GithubUIUtil.avatarSize, accountList)
          if (correctScopes) {
            errorText = null
            showReLoginLink = false
          }
          else {
            errorText = "Insufficient security scopes"
            showReLoginLink = true
          }
        })
      }

      override fun onThrowable(error: Throwable) {
        accountListModel.contentsChanged(accountData.apply {
          errorText = error.message.toString()
          showReLoginLink = error is GithubAuthenticationException
        })
      }
    })
  }

  private fun createListProgressManager() = object : ProgressVisibilityManager() {
    override fun setProgressVisible(visible: Boolean) = accountList.setPaintBusy(visible)
    override fun getModalityState() = ModalityState.any()
  }

  fun setAccounts(accounts: Map<GithubAccount, String?>, defaultAccount: GithubAccount?) {
    accountListModel.removeAll()
    accountListModel.addAll(0, accounts.keys.map { GithubAccountDecorator(it, it == defaultAccount) })
    currentTokensMap = accounts
  }

  /**
   * @return list of accounts and associated tokens if new token was created and selected default account
   */
  fun getAccounts(): Pair<Map<GithubAccount, String?>, GithubAccount?> {
    return accountListModel.items.associate { it.account to newTokensMap[it.account] } to
      accountListModel.items.find { it.projectDefault }?.account
  }

  fun clearNewTokens() = newTokensMap.clear()

  fun isModified(accounts: Set<GithubAccount>, defaultAccount: GithubAccount?): Boolean {
    return accountListModel.items.find { it.projectDefault }?.account != defaultAccount ||
           accountListModel.items.map { it.account }.toSet() != accounts ||
           newTokensMap.isNotEmpty()
  }

  override fun dispose() {}
}

private class GithubAccountDecoratorRenderer : ListCellRenderer<GithubAccountDecorator>, JPanel() {
  private val accountName = JLabel()

  private val serverName = JLabel()
  private val profilePicture = JLabel()

  private val fullName = JLabel()

  private val loadingError = SimpleColoredComponent()

  /**
   * UPDATE [createLinkActivationListener] IF YOU CHANGE LAYOUT
   */
  init {
    layout = FlowLayout(FlowLayout.LEFT, 0, 0)
    border = JBUI.Borders.empty(5, 8)

    val namesPanel = JPanel().apply {
      layout = GridBagLayout()
      border = JBUI.Borders.empty(0, 6, 4, 6)

      val bag = GridBag()
        .setDefaultInsets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP))
        .setDefaultAnchor(GridBagConstraints.WEST)
        .setDefaultFill(GridBagConstraints.VERTICAL)
      add(fullName, bag.nextLine().next())
      add(accountName, bag.next())
      add(loadingError, bag.next())
      add(serverName, bag.nextLine().coverLine())
    }

    add(profilePicture)
    add(namesPanel)
  }

  override fun getListCellRendererComponent(list: JList<out GithubAccountDecorator>,
                                            value: GithubAccountDecorator,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

    accountName.apply {
      text = value.account.name
      setBold(if (value.details?.name == null) value.projectDefault else false)
      foreground = if (value.details?.name == null) primaryTextColor else secondaryTextColor
    }
    serverName.apply {
      text = value.account.server.toString()
      foreground = secondaryTextColor
    }
    profilePicture.apply {
      icon = value.getIcon()
    }
    fullName.apply {
      text = value.details?.name
      setBold(value.projectDefault)
      isVisible = value.details?.name != null
      foreground = primaryTextColor
    }
    loadingError.apply {
      clear()
      value.errorText?.let {
        append(it, SimpleTextAttributes.ERROR_ATTRIBUTES)
        append(" ")
        if (value.showReLoginLink) append(GithubBundle.message("accounts.relogin"),
                                          if (value.errorLinkPointedAt)
                                            SimpleTextAttributes(STYLE_UNDERLINE, JBUI.CurrentTheme.Link.linkColor())
                                          else
                                            SimpleTextAttributes(STYLE_PLAIN, JBUI.CurrentTheme.Link.linkColor()),
                                          LINK_TAG)
      }
    }
    return this
  }

  companion object {
    private fun JLabel.setBold(isBold: Boolean) {
      font = font.deriveFont(if (isBold) font.style or Font.BOLD else font.style and Font.BOLD.inv())
    }
  }
}

/**
 * Account + auxillary info + info loading error
 */
private class GithubAccountDecorator(val account: GithubAccount, var projectDefault: Boolean) {
  var details: GithubAuthenticatedUser? = null
  var iconProvider: GHAvatarIconsProvider? = null

  var errorText: String? = null
  var showReLoginLink = false
  var errorLinkPointedAt = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GithubAccountDecorator

    if (account != other.account) return false

    return true
  }

  override fun hashCode(): Int {
    return account.hashCode()
  }

  fun getIcon(): Icon? {
    val url = details?.avatarUrl
    return iconProvider?.getIcon(url)
  }
}
