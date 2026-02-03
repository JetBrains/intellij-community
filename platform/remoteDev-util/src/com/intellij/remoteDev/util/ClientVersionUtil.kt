package com.intellij.remoteDev.util

import com.intellij.openapi.util.BuildNumber
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
  private val sameDefaultPathsAsLocalIdesUsedSince: BuildNumber
    get() = BuildNumber("", 242, 13718)

  fun isSeparateConfigSupported(clientBuild: BuildNumber): Boolean = 
    clientBuild >= separateConfigSupportedSince || clientBuild.baselineVersion == 232 && clientBuild >= separateConfigSupportedSince232

  fun isClientUsesTheSamePathsAsLocalIde(clientVersion: String): Boolean {
    val clientBuild = BuildNumber.fromString(clientVersion)
    return clientBuild != null && clientBuild >= sameDefaultPathsAsLocalIdesUsedSince
  }

  /**
   * Returns value which should be assigned to 'JBC_SEPARATE_CONFIG' environment variable to explicitly enable or disable the "separate 
   * process per connection" mode.
   */
  fun computeSeparateConfigEnvVariableValue(clientBuild: BuildNumber): String? {
    if (isSeparateConfigSupported(clientBuild) &&
        !(clientBuild >= separateConfigEnabledByDefaultSince ||
          clientBuild.baselineVersion == 232 && clientBuild >= separateConfigEnabledByDefaultSince232)) {
      return true.toString()
    }
    return null
  }
}