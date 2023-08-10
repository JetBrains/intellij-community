package com.intellij.remoteDev.util

import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ClientVersionUtil {
  private val separateConfigSupportedSince: BuildNumber
    get() = BuildNumber("", 233, 173)
  private val separateConfigSupportedSince232: BuildNumber
    get() = BuildNumber("", 232, 8661)
  private val separateConfigEnabledByDefaultSince: BuildNumber
    get() = BuildNumber("", 233, 2350)
  private val separateConfigEnabledByDefaultSince232: BuildNumber
    get() = BuildNumber("", 232, 9552)

  fun isJBCSeparateConfigSupported(clientVersion: String): Boolean {
    val clientBuild = BuildNumber.fromString(clientVersion)
    return clientBuild != null && isSeparateConfigSupported(clientBuild)
  }

  private fun isSeparateConfigSupported(clientBuild: BuildNumber) = 
    clientBuild >= separateConfigSupportedSince || clientBuild.baselineVersion == 232 && clientBuild >= separateConfigSupportedSince232

  /**
   * Returns value which should be assigned to 'JBC_SEPARATE_CONFIG' environment variable to explicitly enable or disable the "separate 
   * process per connection" mode.
   */
  fun computeSeparateConfigEnvVariableValue(clientVersion: String): String? {
    val clientBuild = BuildNumber.fromString(clientVersion) ?: return null
    val enableProcessPerConnection = Registry.`is`("rdct.enable.per.connection.client.process")
    if (isSeparateConfigSupported(clientBuild) &&
        !(enableProcessPerConnection && (clientBuild >= separateConfigEnabledByDefaultSince || 
                                         clientBuild.baselineVersion == 232 && clientBuild >= separateConfigEnabledByDefaultSince232))) {
      return enableProcessPerConnection.toString()
    }
    return null
  }
}