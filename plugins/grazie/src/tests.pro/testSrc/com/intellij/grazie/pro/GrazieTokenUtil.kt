package com.intellij.grazie.pro

import com.intellij.util.SystemProperties
import kotlin.io.path.Path
import kotlin.io.path.readText

object GrazieTokenUtil {
  const val ENVIRONMENT_VARIABLE_KEY: String = "AI_ASSISTANT_GRAZIE_TOKEN"
  const val TOKEN_FILE_NAME: String = ".ai-assistant-staging-test-token"

  fun getTestToken(): String {
    val tokenFile = Path(SystemProperties.getUserHome(), TOKEN_FILE_NAME)
    val tokenFromEnvironmentVariable = System.getenv(ENVIRONMENT_VARIABLE_KEY)
    return tokenFromEnvironmentVariable?.trim() ?: tokenFile.readText().trim()
  }
}
