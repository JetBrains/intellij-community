package com.intellij.grazie.cloud.license

import com.intellij.ui.JBAccountInfoService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class JbaToken(val value: String) {
  companion object {
    fun obtain(): JbaToken? {
      val token = JBAccountInfoService.getInstance()?.idToken ?: return null
      return JbaToken(value = token)
    }
  }
}

internal sealed class JbaLoginException(message: String): IllegalStateException(message) {
  class AccountServiceUnavailable: JbaLoginException(message = "Account service is not available")
  class LoginProcess: JbaLoginException(message = "Login process failed")
}

@Throws(JbaLoginException::class)
internal suspend fun performJbaLogin(): JbaToken? {
  val service = JBAccountInfoService.getInstance() ?: throw JbaLoginException.AccountServiceUnavailable()
  suspendCoroutine { continuation ->
    service.invokeJBALogin(
      { id -> continuation.resume(id) },
      { continuation.resumeWithException(JbaLoginException.LoginProcess()) }
    )
  }
  return JbaToken.obtain()
}
