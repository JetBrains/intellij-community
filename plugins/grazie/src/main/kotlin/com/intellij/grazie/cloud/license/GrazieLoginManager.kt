package com.intellij.grazie.cloud.license

import ai.grazie.model.cloud.exceptions.HTTPStatusException
import ai.grazie.nlp.langs.Language
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.cloud.GrazieCloudConfig
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.cloud.GrazieHttpClientManager
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.utils.catching
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.provisioner.ProvisionedServiceConfigurationResult
import com.intellij.platform.ide.provisioner.ProvisionedServiceRegistry
import com.intellij.platform.ide.provisioner.endpoint.AuthTokenResult
import com.intellij.ui.JBAccountInfoService.AuthStateListener
import com.intellij.util.ExceptionUtil
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean

sealed interface GrazieLoginState {
  data object NoJba : GrazieLoginState

  data object WaitingForJba : GrazieLoginState

  data class Jba(val token: JbaToken, val error: String?) : GrazieLoginState

  /** The AI Enterprise support in the IDE is enabled, possibly with errors (reflected in the state's field values) */
  data class Enterprise(val state: EnterpriseState) : GrazieLoginState

  data object WaitingForLicense : GrazieLoginState

  data object WaitingForCloud : GrazieLoginState

  data class Cloud(val grazieToken: String) : GrazieLoginState

  val isCloudConnected: Boolean
    get() = this is Cloud || (this as? Enterprise)?.state?.isAIEnterpriseConnected() == true
}

data class EnterpriseState(val headers: Map<String, String>, val error: String?, val endpoint: String?) {
  fun isAIEnterpriseEnabled() = headers.isNotEmpty() || error != null || endpoint != null
  fun isAIEnterpriseConnected() = headers.isNotEmpty() && endpoint != null && error == null
}

private val noAIEnterprise = EnterpriseState(emptyMap(), null, null)

private val logger = logger<GrazieLoginManager>()

@Service(Service.Level.APP)
class GrazieLoginManager(coroutineScope: CoroutineScope) {
  private val licenseState = MutableStateFlow<LicenseState>(LicenseState.Empty)
  private val loginState = MutableStateFlow<GrazieLoginState>(GrazieLoginState.NoJba)
  private val enterpriseState = MutableStateFlow<EnterpriseState?>(null)
  private val initialized = AtomicBoolean(false)

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      coroutineScope.launch {
        val aiService = service<ProvisionedServiceRegistry>().getServiceById("ai")
        if (aiService == null) {
          logger.trace("No AI service is provisioned")
          enterpriseState.value = noAIEnterprise
        }
        else {
          aiService.configurationFlow.collect { result ->
            logger.trace("Provisioner state changed: $result")
            computeEnterpriseState(result)
          }
        }
      }

      coroutineScope.launch {
        enterpriseState.collectLatest {
          computeLoginState()
          initialized.set(true)
        }
      }

      coroutineScope.launch {
        updateLicense()

        licenseState.collectLatest { license ->
          logger.trace("License state changed: $license")
          computeLoginState()
          initialized.set(true)
        }
      }
      application.messageBus.connect(coroutineScope).subscribe(AuthStateListener.TOPIC, AuthStateListener {
        logger.debug { "Scheduling license and login manager updates on licensing state change" }
        coroutineScope.launch { updateLicense() }
      })
    }
  }

  private suspend fun computeEnterpriseState(result: ProvisionedServiceConfigurationResult) {
    if (result is ProvisionedServiceConfigurationResult.Success.ServiceProvisioned) {
      val configuration = result.configuration
      val endpoint = configuration.endpoint
      if (!configuration["aiEnabled"].toBoolean() || endpoint == null) {
        enterpriseState.value =
          EnterpriseState(emptyMap(), GrazieBundle.message("ai.service.not.provisioned.message"), null)
      }
      else {
        endpoint.authTokenFlow.collect { token ->
          logger.trace("Provisioner auth token state changed: $token")
          when (token) {
            is AuthTokenResult.Success -> enterpriseState.value = EnterpriseState(token.token.requestHeaders, null, endpoint.serverUrl)
            is AuthTokenResult.Failure -> enterpriseState.value = EnterpriseState(emptyMap(), token.message, null)
          }
        }
      }
    }
    else {
      enterpriseState.value = noAIEnterprise
    }
  }

  @VisibleForTesting
  fun setState(state: GrazieLoginState) {
    logger.trace("Login state changed: $state")
    loginState.update { state }
  }

  @TestOnly
  fun isInitialized(): Boolean {
    return initialized.get()
  }

  @Suppress("UnstableApiUsage")
  private fun isChina() = RegionSettings.getRegion() == Region.CHINA

  private suspend fun computeLoginState(force: Boolean = false, skipCloudLogin: Boolean = false) {
    if (GrazieCloudConnector.hasAdditionalConnectors()) return
    val es = enterpriseState.first { it != null }!!
    if (es.isAIEnterpriseEnabled()) {
      setState(GrazieLoginState.Enterprise(if (es.error == null) checkNlpServiceAvailability(es) else es))
      return
    }

    var accountToken = JbaToken.obtain()
    if (accountToken == null && force) {
      openLogInDialog()
      accountToken = JbaToken.obtain()
    }
    if (accountToken == null) {
      setState(GrazieLoginState.NoJba)
      return
    }
    if (isLoggedOut && !force) {
      logger.trace("User is logged out of cloud mode")
      return
    }
    isLoggedOut = false
    if (!activationDialogWasShown) {
      logger.debug("Need explicit activation dialog agreement, so skipping cloud login")
      setState(GrazieLoginState.Jba(token = accountToken, null))
      return
    }

    if (isChina()) {
      setState(GrazieLoginState.Jba(token = accountToken, GrazieBundle.message("unavailable.in.china.dialog.message")))
      return
    }

    setState(GrazieLoginState.WaitingForLicense)
    val result = catching {
      registerIfNeeded(accountToken)
      val license = obtainLicense(accountToken)
      logger.trace("Obtained license: $license")
      if (license == null) {
        setState(GrazieLoginState.Jba(token = accountToken, null))
        return@catching
      }
      if (skipCloudLogin) {
        setState(GrazieLoginState.Jba(token = accountToken, null))
        isLoggedOut = true
        return@catching
      }
      setState(GrazieLoginState.WaitingForCloud)
      val cloudToken = obtainCloudToken(token = accountToken, license = license)
      logger.trace("Obtained cloud token: $cloudToken")
      setState(GrazieLoginState.Cloud(cloudToken))
    }
    val exception = result.exceptionOrNull()
    if (exception != null) {
      logger.warn("Login state update failed", exception)
      setState(GrazieLoginState.Jba(token = accountToken, extractRootMessage(exception)?.takeIf { it.isNotBlank() }))
    }
  }

  private suspend fun checkNlpServiceAvailability(state: EnterpriseState): EnterpriseState {
    val api = GrazieCloudConfig.enterpriseClient(state, service<GrazieHttpClientManager>().instance)
    try {
      api.gec().check(listOf("Hello"), Language.ENGLISH)
      api.trf().nlc().complete("Hello", Language.ENGLISH)
    }
    catch (e: Throwable) {
      logger.info(e)
      if (e is HTTPStatusException && e.status == 404) {
        return state.copy(error = GrazieBundle.message("nlp.services.not.available.message"))
      }
    }
    return state
  }

  // During internal service calls, the platform wraps the original messages into
  // "Exception during request to URL with code X", and we don't want to show these details in the UI
  private val platformMessageWrappingSuffix = Regex("with code \\d{3}: ")

  private fun extractRootMessage(exception: Throwable): String? {
    val message = ExceptionUtil.getRootCause(exception).message ?: return null
    val lastMatch = platformMessageWrappingSuffix.findAll(message).lastOrNull()
    return if (lastMatch != null) message.substring(lastMatch.range.last + 1) else message
  }

  private suspend fun registerIfNeeded(token: JbaToken) {
    val api = GrazieCloudConfig.api()
    try {
      val info = api.auth().userInfo(token.value)
      logger.trace("UserInfo: $info")
    }
    catch (exception: HTTPStatusException.NotFound) {
      val state = api.auth().register(token.value)
      logger.trace("UserState: $state")
    }
  }

  fun state(): StateFlow<GrazieLoginState> {
    return loginState.asStateFlow()
  }

  suspend fun invokeJbaLogin() {
    // Just NoJba?
    val stateBefore = loginState.getAndUpdate { GrazieLoginState.WaitingForJba }
    when (performJbaLogin()) {
      null -> loginState.update { stateBefore }
      else -> updateLicense()
    }
  }

  suspend fun logOutFromCloud() {
    computeLoginState(skipCloudLogin = true)
  }

  suspend fun logInToCloud(force: Boolean): GrazieLoginState {
    computeLoginState(force)
    return loginState.value
  }

  private suspend fun updateLicense() {
    val license = LicenseState.obtain()
    logger.trace("Updating license with: $license")
    if (license != licenseState.value && (license == LicenseState.NoLicense || license == LicenseState.Invalid)) {
      activationDialogWasShown = false
    }
    licenseState.update { license }
  }

  companion object {
    fun state(): StateFlow<GrazieLoginState> {
      return getInstance().state()
    }

    val lastState: GrazieLoginState
      get() = state().value

    suspend fun invokeJbaLogin() {
      getInstance().invokeJbaLogin()
    }

    suspend fun logOutFromCloud() {
      getInstance().logOutFromCloud()
    }

    suspend fun logInToCloud(force: Boolean): GrazieLoginState {
      return getInstance().logInToCloud(force)
    }

    @JvmStatic
    fun getInstance(): GrazieLoginManager = service<GrazieLoginManager>()

    fun subscribeWithState(disposable: Disposable, listener: (GrazieLoginState) -> Unit) {
      GrazieScope.coroutineScope().launchDisposable(disposable) {
        state().collect { listener(it) }
      }
    }

    fun subscribe(disposable: Disposable, listener: () -> Unit) {
      GrazieScope.coroutineScope().launchDisposable(disposable) {
        state().collect { listener() }
      }
    }
  }
}

private suspend fun obtainCloudToken(license: LicenseState.Valid, token: JbaToken): String {
  val api = GrazieCloudConfig.api()
  val info = api.auth().getAccessV2(token.value, license.data.id)
  logger.trace("JbaInfo: $info")
  return info.token
}

private suspend fun obtainLicense(token: JbaToken): LicenseState.Valid? {
  var license = LicenseState.obtain()
  logger.trace("Obtained local license: $license")
  if (license is LicenseState.Valid) {
    return license
  }
  if (license is LicenseState.Empty || license is LicenseState.Invalid) {
    return null
  }
  logger.trace("Fetching a license from the cloud backend")
  val httpClient = service<GrazieHttpClientManager>().withExtendedTimeout
  val external = GrazieCloudConfig.api(httpClient).auth().license().obtainGrazieLite(token.value) ?: return null
  logger.trace("Obtained external license from cloud: $external")
  check(!external.cancelled) { "Issued license is cancelled" }
  check(!external.suspended) { "Issued license is suspended" }
  check(!external.outdated) { "Issued license is outdated" }

  // Try to get a license one more time after Grazie lite was granted.
  license = LicenseState.obtain()
  if (license is LicenseState.Valid) {
    return license
  }

  // Well, we tried. The user will be able to log in to the cloud via Grazie Icon on the status bar
  return null
}

internal suspend fun performJbaLogin(): JbaToken? {
  openLogInDialog()
  return JbaToken.obtain()
}

internal fun ensureTermsOfServiceShown(): Boolean {
  if (activationDialogWasShown) return true

  val result = Messages.showYesNoDialog(
    null,
    GrazieBundle.message("grazie.terms.of.service.dialog.message"),
    GrazieBundle.message("grazie.terms.of.service.dialog.title"),
    GrazieBundle.message("grazie.terms.of.service.dialog.ok.button"),
    GrazieBundle.message("grazie.terms.of.service.dialog.cancel.button"),
    GrazieIcons.GrazieCloudProcessing
  )
  logger<GrazieLoginManager>().debug("Terms of service dialog shown, result: $result")
  if (result == Messages.YES) {
    activationDialogWasShown = true
    return true
  }
  return false
}

private const val ActivationDialogShownKey = "grazie.cloud.activation.dialog.shown"
private var activationDialogWasShown: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(ActivationDialogShownKey, false)
  set(value) = PropertiesComponent.getInstance().setValue(ActivationDialogShownKey, value)

private const val LoggedOutKey = "grazie.cloud.logged.out"
private var isLoggedOut: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(LoggedOutKey, false)
  set(value) = PropertiesComponent.getInstance().setValue(LoggedOutKey, value)

