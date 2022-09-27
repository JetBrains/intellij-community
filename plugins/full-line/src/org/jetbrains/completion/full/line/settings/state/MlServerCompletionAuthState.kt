package org.jetbrains.completion.full.line.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
  name = "MlServerCompletionAuthState",
  storages = [Storage("MlServerCompletionAuthState.xml", deprecated = true), Storage("full.line.auth.xml")]
)
class MlServerCompletionAuthState : PersistentStateComponent<MlServerCompletionAuthState.State> {
  private var state = State()

  override fun initializeComponent() {
    loadEvaluationConfig()
  }

  override fun getState() = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun isVerified() = state.verified == FLVerificationStatus.VERIFIED

  fun authToken() = state.authToken

  private fun loadEvaluationConfig() {
    val isTesting = System.getenv("flcc_evaluating") ?: return
    if (!isTesting.toBoolean()) return

    System.getenv("flcc_token")?.toString()?.let { state.authToken = it }
    System.getenv("flcc_internal")?.toBoolean()?.let { state.verified = FLVerificationStatus.fromBool(it) }
  }

  data class State(
    var authToken: String = "",
    var verified: FLVerificationStatus = FLVerificationStatus.UNKNOWN
  )

  enum class FLVerificationStatus {
    UNKNOWN, VERIFIED, UNVERIFIED;

    companion object {
      fun fromBool(value: Boolean) = if (value) VERIFIED else UNVERIFIED
      fun fromStatusCode(value: Int) = when (value) {
        200 -> VERIFIED
        404 -> UNKNOWN
        else -> UNVERIFIED
      }
    }
  }

  companion object {
    fun getInstance(): MlServerCompletionAuthState {
      return service()
    }
  }
}
