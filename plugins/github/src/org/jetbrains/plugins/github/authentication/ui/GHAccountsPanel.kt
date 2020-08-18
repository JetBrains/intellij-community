// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativePoint.getCenterOf
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.progress.ProgressVisibilityManager
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.JListHoveredRowMaterialiser
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.*
import javax.swing.*

private val actionManager: ActionManager get() = ActionManager.getInstance()

internal class GHAccountsPanel(
  private val project: Project,
  private val executorFactory: GithubApiRequestExecutor.Factory,
  private val avatarLoader: CachingGithubUserAvatarLoader,
  private val imageResizer: GithubImageResizer
) : BorderLayoutPanel(), Disposable, DataProvider {

  companion object {
    val KEY: DataKey<GHAccountsPanel> = DataKey.create("Github.AccountsPanel")
  }

  private val accountListModel = CollectionListModel<GithubAccountDecorator>()
  private val accountList = JBList<GithubAccountDecorator>(accountListModel).apply {
    val decoratorRenderer = GithubAccountDecoratorRenderer()
    cellRenderer = decoratorRenderer
    JListHoveredRowMaterialiser.install(this, GithubAccountDecoratorRenderer())
    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(decoratorRenderer))

    selectionMode = ListSelectionModel.SINGLE_SELECTION
  }

  private val progressManager = createListProgressManager()
  private var currentTokensMap = mapOf<GithubAccount, String?>()
  private val newTokensMap = mutableMapOf<GithubAccount, String>()

  init {
    accountList.emptyText.apply {
      appendText(GithubBundle.message("accounts.none.added"))
      appendSecondaryText(GithubBundle.message("accounts.add"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        invokeAction(actionManager.getAction("Github.Accounts.AddGHAccount"), accountList, ActionPlaces.UNKNOWN, null, null)
      }
      appendSecondaryText(" (${KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew())})", StatusText.DEFAULT_ATTRIBUTES, null)
    }

    addToCenter(ToolbarDecorator.createDecorator(accountList)
                  .disableUpDownActions()
                  .setAddAction { showAddAccountActions(it.preferredPopupPoint ?: getCenterOf(accountList)) }
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

  override fun getData(dataId: String): Any? {
    if (KEY.`is`(dataId)) return this
    return null
  }

  private fun showAddAccountActions(point: RelativePoint) {
    val group = actionManager.getAction("Github.Accounts.AddAccount") as ActionGroup
    val popup = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group)

    popup.setTargetComponent(this)
    popup.component.show(point.component, point.point.x, point.point.y)
  }

  private fun editAccount(decorator: GithubAccountDecorator) {
    val authData = GithubAuthenticationManager.getInstance().login(
      project, this,
      GHLoginRequest(server = decorator.account.server, login = decorator.account.name)
    )
    if (authData == null) return

    decorator.account.name = authData.login
    newTokensMap[decorator.account] = authData.token
    loadAccountDetails(decorator)
  }

  fun addAccount(server: GithubServerPath, login: String, token: String) {
    val githubAccount = GithubAccountManager.createAccount(login, server)
    newTokensMap[githubAccount] = token

    val accountData = GithubAccountDecorator(githubAccount, false)
    accountListModel.add(accountData)
    loadAccountDetails(accountData)
  }

  fun isAccountUnique(login: String, server: GithubServerPath) =
    accountListModel.items.none { it.account.name == login && it.account.server == server }

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

  private inner class GithubAccountDecoratorRenderer : ListCellRenderer<GithubAccountDecorator>, JPanel() {
    private val accountName = JLabel()

    private val serverName = JLabel()
    private val profilePicture = JLabel()

    private val fullName = JLabel()

    private val loadingError = JLabel()
    private val reloginLink = LinkLabel<Any?>(GithubBundle.message("login.action"), null)

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
        add(reloginLink, bag.next())
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
        text = value.errorText
        foreground = UIUtil.getErrorForeground()
      }
      reloginLink.apply {
        isVisible = value.errorText != null && value.showReLoginLink
        setListener(LinkListener { _, _ ->
          editAccount(value)
        }, null)
      }
      return this
    }


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
