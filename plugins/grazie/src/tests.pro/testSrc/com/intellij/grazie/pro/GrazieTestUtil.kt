package com.intellij.grazie.pro

import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.SystemProperties
import kotlin.io.path.Path
import kotlin.io.path.readText

object GrazieTestUtil {
  const val ENVIRONMENT_VARIABLE_KEY: String = "AI_ASSISTANT_GRAZIE_TOKEN"
  const val TOKEN_FILE_NAME: String = ".ai-assistant-staging-test-token"

  fun getTestToken(): String {
    val tokenFile = Path(SystemProperties.getUserHome(), TOKEN_FILE_NAME)
    val tokenFromEnvironmentVariable = System.getenv(ENVIRONMENT_VARIABLE_KEY)
    return tokenFromEnvironmentVariable?.trim() ?: tokenFile.readText().trim()
  }

  @JvmStatic
  fun registerGrazieCloudConnectorWithQuota(disposable: Disposable) {
    val pointName = ExtensionPointName<GrazieCloudConnector>("com.intellij.grazie.cloudConnector")
    val connectorWithQuota = object : GrazieCloudConnector by pointName.extensionList.first() {
      override fun hasQuota(): Boolean = true
    }
    ExtensionTestUtil.maskExtensions(pointName, listOf(connectorWithQuota), disposable)
  }
}
