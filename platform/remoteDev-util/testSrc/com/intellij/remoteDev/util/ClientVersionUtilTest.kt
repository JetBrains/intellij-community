package com.intellij.remoteDev.util

import com.intellij.openapi.util.BuildNumber
import com.intellij.remoteDev.util.ClientVersionUtil.computeSeparateConfigEnvVariableValue
import com.intellij.remoteDev.util.ClientVersionUtil.isSeparateConfigSupported
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ClientVersionUtilTest {
  @ValueSource(strings = ["232.9552", "232.9559.2", "232.SNAPSHOT", "233.2350", "233.SNAPSHOT", "241.1", "241.SNAPSHOT"])
  @ParameterizedTest
  fun `separate config supported and enabled`(version: String) {
    val buildNumber = BuildNumber.fromString(version)!!
    assertTrue(isSeparateConfigSupported(buildNumber))
    assertNull(computeSeparateConfigEnvVariableValue(buildNumber))
  }

  @ValueSource(strings = ["233.172", "232.8660", "232.8660.185"])
  @ParameterizedTest
  fun `separate config not supported`(version: String) {
    val buildNumber = BuildNumber.fromString(version)!!
    assertFalse(isSeparateConfigSupported(buildNumber))
    assertNull(computeSeparateConfigEnvVariableValue(buildNumber))
  }

  @ValueSource(strings = ["232.8661", "233.173", "233.2349"])
  @ParameterizedTest
  fun `separate config supported but not enabled`(version: String) {
    val buildNumber = BuildNumber.fromString(version)!!
    assertTrue(isSeparateConfigSupported(buildNumber))
    assertEquals("true", computeSeparateConfigEnvVariableValue(buildNumber))
  }
}