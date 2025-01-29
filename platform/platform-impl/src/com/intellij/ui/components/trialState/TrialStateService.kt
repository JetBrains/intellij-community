// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.trialState

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GotItTextBuilder
import com.intellij.ui.GotItTooltip
import com.intellij.ui.LicensingFacade
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.RestartDialogImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
internal class TrialStateService(private val scope: CoroutineScope) : Disposable {

  enum class TrialState {
    TRIAL_STARTED,
    ACTIVE,
    ALERT,
    EXPIRING,
    GRACE,
    GRACE_ENDED
  }

  data class State(
    val trialState: TrialState, val trialStateChanged: Boolean,
    val colorState: TrialStateButton.ColorState, private val remainedDays: Int, private val trialLengthDays: Int,
  ) {

    fun getButtonText(): @NlsContexts.Button String {
      return when (trialState) {
        TrialState.TRIAL_STARTED,
        TrialState.ACTIVE, -> IdeBundle.message("trial.state.trial.started")
        TrialState.ALERT -> IdeBundle.message("trial.state.days.trial.left", remainedDays)
        TrialState.EXPIRING -> IdeBundle.message("trial.state.1.day.trial.left")
        TrialState.GRACE,
        TrialState.GRACE_ENDED,
          -> IdeBundle.message("trial.state.grace.period")
      }
    }

    fun getGotItTooltip(): GotItTooltip? {
      lateinit var result: GotItTooltip
      result = when (trialState) {
        TrialState.TRIAL_STARTED -> GotItTooltip(GOT_IT_ID, IdeBundle.message("trial.state.got.it.trial.started.text"))
          .withHeader(IdeBundle.message("trial.state.got.it.trial.started.title", trialLengthDays))
          .addLearnMoreButton()

        TrialState.ALERT -> GotItTooltip(GOT_IT_ID, IdeBundle.message("trial.state.got.it.days.trial.left.text"))
          .withHeader(IdeBundle.message("trial.state.got.it.days.trial.left.title", remainedDays))
          .addLearnMoreButton()

        TrialState.EXPIRING -> GotItTooltip(GOT_IT_ID, IdeBundle.message("trial.state.got.it.1.day.trial.left.text"))
          .withHeader(IdeBundle.message("trial.state.got.it.1.day.trial.left.title"))
          .addLearnMoreButton()

        TrialState.GRACE -> {
          val textSupplier: GotItTextBuilder.() -> String = {
            buildString {
              append(IdeBundle.message("trial.state.got.it.grace.period.text.begin"))
              append(link(IdeBundle.message("trial.state.got.it.grace.period.text.link")) {
                Disposer.dispose(result)

                TrialStateUtils.showRegister()
              })
              append(IdeBundle.message("trial.state.got.it.grace.period.text.end"))
            }
          }
          GotItTooltip(GOT_IT_ID, textSupplier)
            .withHeader(IdeBundle.message("trial.state.got.it.grace.period.title"))
            .withButtonLabel(IdeBundle.message("trial.state.got.it.restart.ide"))
            .withGotItButtonAction { RestartDialogImpl.restartWithConfirmation() }
        }

        else -> return null
      }

      result.withSecondaryButton(IdeBundle.message("trial.state.got.it.close"))

      // Allow tooltips unlimited times
      PropertiesComponent.getInstance().unsetValue("${GotItTooltip.PROPERTY_PREFIX}.${result.id}")

      return result
    }

    private fun GotItTooltip.addLearnMoreButton(): GotItTooltip {
      return withButtonLabel(IdeBundle.message("trial.state.got.it.learn.more"))
        .withGotItButtonAction { TrialStateUtils.openTrailStateTab() }
    }
  }

  companion object {
    fun getInstance(): TrialStateService = service<TrialStateService>()

    fun isEnabled(): Boolean {
      // Community IDEs don't have the registry key
      return Registry.`is`("trial.state.widget", false) &&
             (PlatformUtils.isPyCharm() || PlatformUtils.isIntelliJ())
    }

    fun isApplicable(): Boolean {
      return LicensingFacade.getInstance()?.isEvaluationLicense == true
    }
  }

  private val mutableState = MutableStateFlow<State?>(null)
  val state: StateFlow<State?> = mutableState.asStateFlow()

  init {
    if (isEnabled()) {
      scope.launch {
        updateState()

        while (true) {
          delay(if (testRemainingDays == null) 1.hours else 1.seconds)
          updateState()
        }
      }

      val messageBus = ApplicationManager.getApplication().messageBus
      messageBus.connect(this).subscribe(LicensingFacade.LicenseStateListener.TOPIC, LicensingFacade.LicenseStateListener {
        scope.launch {
          updateState()
        }
      })
    }
  }

  override fun dispose() {
  }

  private fun updateState() {
    mutableState.value = calculateState()
  }

  @Synchronized
  private fun calculateState(): State? {
    var remainedDays = (TrialStateUtils.getExpiresInDays() ?: return null) + 1
    testRemainingDays?.let {
      remainedDays = it
    }
    val trialLengthDays = TrialStateUtils.getTrialLengthDays()
    val trialState = getTrialState(remainedDays, trialLengthDays) ?: return null
    val newColorState = getColorState(trialState)
    val lastColorState = lastTrialState?.let { getColorState(it) }
    val colorStateChanged = newColorState != lastColorState
    if (colorStateChanged) {
      lastColorStateClicked = false
    }

    val trialStateChanged = lastTrialState != trialState
    if (trialStateChanged) {
      lastTrialState = trialState
    }

    val colorState = if (lastColorStateClicked && newColorState != TrialStateButton.ColorState.EXPIRING) TrialStateButton.ColorState.DEFAULT else newColorState

    return State(trialState = trialState, trialStateChanged = trialStateChanged, colorState = colorState,
                 remainedDays = remainedDays, trialLengthDays = trialLengthDays)
  }

  fun setLastShownColorStateClicked() {
    if (!lastColorStateClicked) {
      lastColorStateClicked = true

      scope.launch {
        updateState()
      }
    }
  }
}

private const val LAST_STATE_KEY = "trial.state.last.state"
private const val LAST_COLOR_CLICKED_KEY = "trial.state.last.color.state.clicked"
private const val ALERT_REMAINED_DAYS = 7
private const val EXPIRING_REMAINED_DAYS = 1
private const val GRACE_DAYS = -1

private const val GOT_IT_ID = "trial.state.widget.got.it.id"

private val testRemainingDays: Int?
  get() {
    val result = Registry.intValue("trial.state.test.remaining.days", -100)
    return if (result == -100) null else result
  }

private var lastTrialState: TrialStateService.TrialState?
  get() = PropertiesComponent.getInstance().getValue(LAST_STATE_KEY)?.let {
    try {
      TrialStateService.TrialState.valueOf(it)
    }
    catch (_: Exception) {
      null
    }
  }
  set(value) = PropertiesComponent.getInstance().setValue(LAST_STATE_KEY, value?.name)

private var lastColorStateClicked: Boolean
  get() = PropertiesComponent.getInstance().getBoolean(LAST_COLOR_CLICKED_KEY, false)
  set(value) = PropertiesComponent.getInstance().setValue(LAST_COLOR_CLICKED_KEY, value)

private fun getColorState(trialState: TrialStateService.TrialState): TrialStateButton.ColorState {
  return when (trialState) {
    TrialStateService.TrialState.TRIAL_STARTED,
    TrialStateService.TrialState.ACTIVE,
      -> TrialStateButton.ColorState.ACTIVE
    TrialStateService.TrialState.ALERT -> TrialStateButton.ColorState.ALERT
    TrialStateService.TrialState.EXPIRING,
    TrialStateService.TrialState.GRACE,
    TrialStateService.TrialState.GRACE_ENDED,
      -> TrialStateButton.ColorState.EXPIRING
  }
}

private fun getTrialState(remainedDays: Int, trialLengthDays: Int): TrialStateService.TrialState? {
  return when {
    trialLengthDays > 0 && remainedDays == trialLengthDays -> TrialStateService.TrialState.TRIAL_STARTED
    remainedDays > ALERT_REMAINED_DAYS -> TrialStateService.TrialState.ACTIVE
    remainedDays > EXPIRING_REMAINED_DAYS -> TrialStateService.TrialState.ALERT
    remainedDays > 0 -> TrialStateService.TrialState.EXPIRING
    remainedDays > GRACE_DAYS -> TrialStateService.TrialState.GRACE
    remainedDays == GRACE_DAYS -> TrialStateService.TrialState.GRACE_ENDED
    else -> null
  }
}
