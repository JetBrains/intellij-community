// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.util.net.OverrideCapableProxySettings.State
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.proxy.JavaProxyProperty
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS

// TODO: shouldn't accept services, should instead accept UI model or some temporary mutable representation of the settings
internal class ProxySettingsUi(
  proxySettings: ProxySettings,
  private val credentialStore: ProxyCredentialStore,
  private val disabledPromptsManager: DisabledProxyAuthPromptsManager
) : ConfigurableUi<ProxySettings> {
  private var mainPanel: JPanel
  private lateinit var proxyLoginTextField: JTextField
  private lateinit var proxyPasswordTextField: JPasswordField
  private lateinit var proxyAuthCheckBox: JCheckBox
  private lateinit var proxyPortTextField: JBIntSpinner
  private lateinit var proxyHostTextField: JTextField
  private lateinit var rememberProxyPasswordCheckBox: JCheckBox
  private lateinit var autoDetectProxyRb: JBRadioButton
  private lateinit var useHttpProxyRb: JBRadioButton
  private lateinit var systemProxyDefinedWarning: JLabel
  private lateinit var noProxyRb: JBRadioButton
  private lateinit var typeHttpRb: JBRadioButton
  private lateinit var typeSocksRb: JBRadioButton
  private lateinit var clearPasswordsButton: JButton
  private lateinit var errorLabel: JLabel
  private lateinit var checkButton: JButton
  private lateinit var javaPropertiesWarning: JLabel
  private lateinit var proxyExceptionsField: RawCommandLineEditor
  private lateinit var pacUrlCheckBox: JCheckBox
  private lateinit var pacUrlTextField: JTextField
  private lateinit var tunnelingAuthSchemeDisabledWarning: JLabel
  private lateinit var pluginOverrideCheckbox: JCheckBox

  private var lastOverriderProvidedConfiguration: State? = null
  private lateinit var lastUserConfiguration: State

  private var checkConnectionUrl = "https://example.com/"
  private var lastProxyError: @NlsSafe String = ""

  private fun getMainPanel() = mainPanel

  init {
    // TODO would be nice to reimplement it using bindings
    mainPanel = panel {
      row {
        systemProxyDefinedWarning = label(UIBundle.message("proxy.system.label")).applyToComponent {
          icon = Messages.getWarningIcon()
          isVisible = System.getProperty(JavaProxyProperty.USE_SYSTEM_PROXY).toBoolean()
        }.component
      }
      row {
        javaPropertiesWarning = label("").applyToComponent { icon = Messages.getWarningIcon() }.component
      }
      row {
        pluginOverrideCheckbox = checkBox(UIBundle.message("proxy.settings.override.by.plugin.checkbox", "")).actionListener { _, checkbox ->
          resetByOverrideState(checkbox.isSelected)
        }.component
      }
      buttonsGroup {
        row {
          noProxyRb = radioButton(UIBundle.message("proxy.direct.rb")).component
        }
        row {
          autoDetectProxyRb = radioButton(UIBundle.message("proxy.pac.rb")).component
          link(UIBundle.message("proxy.system.proxy.settings")) {
            try {
              SystemProxySettings.getInstance().openProxySettings()
            }
            catch (e: Exception) {
              logger<ProxySettingsUi>().error("failed to open system proxy settings", e)
            }
          }.applyToComponent {
            setExternalLinkIcon()
            autoHideOnDisable = true
            isEnabled = SystemProxySettings.getInstance().isProxySettingsOpenSupported()
          }.align(AlignX.RIGHT)
        }
        indent {
          row {
            pacUrlCheckBox = checkBox(UIBundle.message("proxy.pac.url.label")).component
            pacUrlTextField = textField().align(AlignX.FILL).comment(UIBundle.message("proxy.pac.url.example")).enabledIf(pacUrlCheckBox.selected).component
          }
          row {
            clearPasswordsButton = button(UIBundle.message("proxy.pac.pw.clear.button")) {
              credentialStore.clearAllCredentials()
              Messages.showMessageDialog(
                getMainPanel(), IdeBundle.message("message.text.proxy.passwords.were.cleared"), IdeBundle.message("dialog.title.auto.detected.proxy"), Messages.getInformationIcon()
              )
            }.component
          }
        }.enabledIf(autoDetectProxyRb.selected and pluginOverrideCheckbox.selected.not())
        row {
          useHttpProxyRb = radioButton(UIBundle.message("proxy.manual.rb")).component
        }
        indent {
          buttonsGroup {
            row {
              typeHttpRb = radioButton(UIBundle.message("proxy.manual.type.http")).component
              typeSocksRb = radioButton(UIBundle.message("proxy.manual.type.socks")).component
            }
          }
          panel {
            row(UIBundle.message("proxy.manual.host")) {
              proxyHostTextField = textField().align(AlignX.FILL).component
            }
            row(UIBundle.message("proxy.manual.port")) {
              proxyPortTextField = spinner(0..65535).component
            }
            row(UIBundle.message("proxy.manual.exclude")) {
              proxyExceptionsField = RawCommandLineEditor(
                { text ->
                  val result = ArrayList<String>()
                  for (token in text.split(",")) {
                    val trimmedToken = token.trim()
                    if (!trimmedToken.isEmpty()) {
                      result.add(trimmedToken)
                    }
                  }
                  result
                }, { StringUtil.join(it, ", ") }
              )
              cell(proxyExceptionsField).align(AlignX.FILL).comment(UIBundle.message("proxy.manual.exclude.example"))
            }
          }
          row {
            proxyAuthCheckBox = checkBox(UIBundle.message("proxy.manual.auth")).component
          }
          indent {
            panel {
              row(UIBundle.message("auth.login.label")) {
                proxyLoginTextField = textField().align(AlignX.FILL).component
              }
              row(UIBundle.message("auth.password.label")) {
                proxyPasswordTextField = passwordField().align(AlignX.FILL).component
              }
              row("") {
                rememberProxyPasswordCheckBox = checkBox(UIBundle.message("auth.remember.cb")).component
              }
            }
          }.enabledIf(proxyAuthCheckBox.selected and pluginOverrideCheckbox.selected.not())
        }.enabledIf(useHttpProxyRb.selected and pluginOverrideCheckbox.selected.not())
      }
      indent {
        row {
          tunnelingAuthSchemeDisabledWarning = label(UIBundle.message("proxy.tunneling.disabled.warning")).applyToComponent {
            icon = Messages.getWarningIcon()
            isVisible = JavaNetworkUtils.isTunnelingAuthSchemeDisabled(JavaNetworkUtils.BASIC_AUTH_SCHEME)
          }.component
        }
      }
      row {
        errorLabel = label("").applyToComponent {
          icon = Messages.getErrorIcon()
        }.component
      }
      row {
        checkButton = button(UIBundle.message("proxy.test.button")) {
          doCheckConnection()
        }.applyToComponent {
          isVisible = proxySettings === ProxySettings.getInstance()
        }.component
      }
    }

    noProxyRb.setSelected(true)
    typeHttpRb.setSelected(true)
  }

  private fun doCheckConnection() {
    parseProxyConfiguration().getOrElse {
      Messages.showErrorDialog(mainPanel, it.message)
      return
    }
    parseCredentials().getOrElse {
      Messages.showErrorDialog(mainPanel, it.message)
      return
    }

    val title = IdeBundle.message("dialog.title.check.proxy.settings")
    val url = Messages.showInputDialog(
      mainPanel, IdeBundle.message("message.text.enter.url.to.check.connection"), title, Messages.getQuestionIcon(), checkConnectionUrl, null
    )
    if (url.isNullOrBlank()) return
    checkConnectionUrl = url
    try {
      apply(ProxySettings.getInstance())
    }
    catch (_: ConfigurationException) {
      return
    }

    val exceptionReference = AtomicReference<Exception>()
    runWithModalProgressBlocking(ModalTaskOwner.component(mainPanel), IdeBundle.message("progress.title.check.connection")) {
      withContext(Dispatchers.IO) {
        try {
          val client = PlatformHttpClient.client()
          val request = PlatformHttpClient.requestBuilder(URI(url)).timeout(Duration.ofSeconds(3)).build()
          PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.discarding()))
        }
        catch (e: Exception) {
          exceptionReference.set(e)
        }
      }
    }
    val exception = exceptionReference.get()
    if (exception == null) {
      Messages.showMessageDialog(mainPanel, IdeBundle.message("message.connection.successful"), title, Messages.getInformationIcon())
    }
    else {
      lastProxyError = IdeBundle.message("dialog.message.problem.with.connection", StringUtil.removeHtmlTags(exception.message.orEmpty()))
      Messages.showErrorDialog(mainPanel, lastProxyError, title)
    }
    reset(ProxySettings.getInstance())
  }

  private fun parseProxyConfiguration(): Result<State> = runCatching {
    val type = when {
      autoDetectProxyRb.isSelected && pacUrlCheckBox.isSelected -> State.Type.PAC
      autoDetectProxyRb.isSelected -> State.Type.AUTO
      useHttpProxyRb.isSelected -> State.Type.STATIC
      else -> State.Type.DIRECT
    }
    val pacUrl = if (autoDetectProxyRb.isSelected && pacUrlCheckBox.isSelected) parsePacUrl() else pacUrlTextField.text?.trim().orEmpty()
    val protocol = if (typeSocksRb.isSelected) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
    val host = if (useHttpProxyRb.isSelected) parseProxyHost() else proxyHostTextField.text?.trim().orEmpty()
    val port = proxyPortTextField.number
    val exceptions = proxyExceptionsField.text?.trim().orEmpty()
    State(type, pacUrl, protocol, host, port, exceptions)
  }

  @Throws(ConfigurationException::class)
  private fun parsePacUrl(): String {
    val pacUrl = pacUrlTextField.text?.trim()
    if (pacUrl.isNullOrBlank()) throw ConfigurationException(IdeBundle.message("dialog.message.url.is.empty"))
    try {
      URI(pacUrl).toURL()
      return pacUrl
    } catch (_: MalformedURLException) {
      throw ConfigurationException(IdeBundle.message("dialog.message.url.is.invalid"))
    } catch (_: URISyntaxException) {
      throw ConfigurationException(IdeBundle.message("dialog.message.url.is.invalid"))
    }
  }

  @Throws(ConfigurationException::class)
  private fun parseProxyHost(): String {
    val host = proxyHostTextField.text?.trim()
    if (host.isNullOrBlank()) throw ConfigurationException(IdeBundle.message("dialog.message.host.name.empty"))
    if (NetUtils.isValidHost(host) == NetUtils.ValidHostInfo.INVALID) throw ConfigurationException(IdeBundle.message("dialog.message.invalid.host.value"))
    return host
  }

  private fun parseCredentials(): Result<Credentials?> = runCatching {
    val login = proxyLoginTextField.text
    val password = proxyPasswordTextField.password
    if (useHttpProxyRb.isSelected && proxyAuthCheckBox.isSelected) {
      if (login.isNullOrBlank()) throw ConfigurationException(IdeBundle.message("dialog.message.login.empty"))
      if (password.isEmpty()) throw ConfigurationException(IdeBundle.message("dialog.message.password.empty"))
    }
    if (login.isNullOrBlank() || password.isEmpty()) null else Credentials(login, password)
  }

  override fun isModified(settings: ProxySettings): Boolean {
    if (settings is OverrideCapableProxySettings && settings.overrideProvider != null) {
      if (settings.isOverrideEnabled != pluginOverrideCheckbox.isSelected) return true
      if (settings.isOverrideEnabled) return false
      // else user configuration is enabled
    }
    val uiConf = parseProxyConfiguration().getOrElse { return true }
    if (settings is OverrideCapableProxySettings) {
      if (uiConf != settings.proxyState) return true
    } else {
      if (uiConf.asProxyConfiguration() != settings.getProxyConfiguration()) return true
    }
    val credentials = parseCredentials().getOrElse { return true }
    val storeCreds = credentialStore.getCredentials(uiConf.PROXY_HOST, uiConf.PROXY_PORT)
    if (credentials != storeCreds) return true
    if (rememberProxyPasswordCheckBox.isSelected != credentialStore.areCredentialsRemembered(uiConf.PROXY_HOST, uiConf.PROXY_PORT)) return true
    return false
  }

  private fun resetProxyConfiguration(state: State) {
    noProxyRb.isSelected = state.PROXY_TYPE == State.Type.DIRECT

    autoDetectProxyRb.isSelected = state.PROXY_TYPE == State.Type.AUTO || state.PROXY_TYPE == State.Type.PAC
    pacUrlCheckBox.isSelected = state.PROXY_TYPE == State.Type.PAC
    pacUrlTextField.text = state.PAC_URL

    useHttpProxyRb.isSelected = state.PROXY_TYPE == State.Type.STATIC
    when (state.PROXY_PROTOCOL) {
      ProxyProtocol.HTTP -> typeHttpRb.isSelected = true
      ProxyProtocol.SOCKS -> typeSocksRb.isSelected = true
    }
    proxyHostTextField.text = state.PROXY_HOST
    proxyPortTextField.number = state.PROXY_PORT
    proxyExceptionsField.text = state.PROXY_EXCEPTIONS

    val creds = credentialStore.getCredentials(state.PROXY_HOST, state.PROXY_PORT)
    proxyAuthCheckBox.isSelected = creds != null
    proxyLoginTextField.text = creds?.userName
    proxyPasswordTextField.text = creds?.password?.toString().orEmpty()
    rememberProxyPasswordCheckBox.isSelected = creds != null && credentialStore.areCredentialsRemembered(state.PROXY_HOST, state.PROXY_PORT)
  }

  private fun resetByOverrideState(overridden: Boolean) {
    if (overridden && lastOverriderProvidedConfiguration != null) {
      noProxyRb.isEnabled = false
      autoDetectProxyRb.isEnabled = false
      useHttpProxyRb.isEnabled = false
      resetProxyConfiguration(lastOverriderProvidedConfiguration!!)
    }
    else {
      noProxyRb.isEnabled = true
      autoDetectProxyRb.isEnabled = true
      useHttpProxyRb.isEnabled = true
      resetProxyConfiguration(lastUserConfiguration)
    }
  }

  override fun reset(settings: ProxySettings) {
    if (settings is OverrideCapableProxySettings) {
      val overrideProvider = settings.overrideProvider

      lastOverriderProvidedConfiguration = overrideProvider?.proxyConfigurationProvider?.getProxyConfiguration()?.let { State().amend(it) }
      lastUserConfiguration = settings.proxyState

      pluginOverrideCheckbox.isVisible = overrideProvider != null
      pluginOverrideCheckbox.isSelected = overrideProvider != null && settings.isOverrideEnabled
      if (overrideProvider != null) {
        val pluginName = settings.getProviderPluginName(overrideProvider) ?: "<unknown>".also {
          logger<ProxySettingsUi>().error("couldn't find plugin descriptor for $overrideProvider")
        }
        pluginOverrideCheckbox.text = UIBundle.message("proxy.settings.override.by.plugin.checkbox", pluginName)
      }
    } else {
      lastOverriderProvidedConfiguration = null
      lastUserConfiguration = State().amend(settings.getProxyConfiguration())
    }
    resetByOverrideState(pluginOverrideCheckbox.isSelected)

    errorLabel.isVisible = lastProxyError.isNotEmpty()
    errorLabel.text = lastProxyError

    @Suppress("DEPRECATION") val javaPropsMessage =
      com.intellij.util.proxy.CommonProxy.getMessageFromProps(com.intellij.util.proxy.CommonProxy.getOldStyleProperties())
    javaPropertiesWarning.isVisible = !javaPropsMessage.isNullOrBlank()
    javaPropertiesWarning.text = javaPropsMessage
  }

  override fun apply(settings: ProxySettings) {
    val modified = isModified(settings)

    val state = parseProxyConfiguration().getOrThrow()
    val credentials = parseCredentials().getOrThrow()
    if (settings is OverrideCapableProxySettings) {
      if (settings.overrideProvider != null) {
        settings.isOverrideEnabled = pluginOverrideCheckbox.isSelected
      }
      settings.proxyState = state
    } else {
      settings.setProxyConfiguration(state.asProxyConfiguration())
    }
    credentialStore.setCredentials(state.PROXY_HOST, state.PROXY_PORT, credentials, rememberProxyPasswordCheckBox.isSelected)

    disabledPromptsManager.enableAllPromptedAuthentications()
    if (modified && JBCefApp.isStarted()) {
      JBCefApp.getNotificationGroup()
        .createNotification(IdeBundle.message("notification.title.jcef.proxyChanged"), IdeBundle.message("notification.content.jcef.applySettings"), NotificationType.WARNING)
        .addAction(NotificationAction.createSimple(IdeBundle.message("action.jcef.restart")) { ApplicationManager.getApplication().restart() })
        .notify(null)
    }

    lastUserConfiguration = state
    lastProxyError = ""
  }

  override fun getComponent(): JComponent {
    mainPanel.setBorder(JBUI.Borders.empty(11, 16, 16, 16))
    val scrollPane = JBScrollPane(mainPanel, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.setBorder(JBUI.Borders.empty())
    return scrollPane
  }
}
