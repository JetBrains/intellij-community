package com.intellij.grazie.cloud.license

import com.intellij.ui.JBAccountInfoService

data class JbaToken(val value: String) {
  companion object {
    fun obtain(): JbaToken? {
      val token = JBAccountInfoService.getInstance()?.idToken ?: return null
      return JbaToken(value = token)
    }
  }
}
