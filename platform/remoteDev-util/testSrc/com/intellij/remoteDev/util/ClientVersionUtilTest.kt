package com.intellij.remoteDev.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.remoteDev.util.ClientVersionUtil.computeSeparateConfigEnvVariableValue
import com.intellij.remoteDev.util.ClientVersionUtil.isJBCSeparateConfigSupported
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ClientVersionUtilTest {
  @TestDisposable
  lateinit var disposable: Disposable
  
  @ValueSource(strings = ["232.9552", "232.9559.2", "232.SNAPSHOT", "233.2350", "233.SNAPSHOT", "241.1", "241.SNAPSHOT"])
  @ParameterizedTest
  fun `separate config supported and enabled`(version: String) {
    assertTrue(isJBCSeparateConfigSupported(version))
    assertNull(computeSeparateConfigEnvVariableValue(version))
    disableProcessPerConnection()
    assertEquals("false", computeSeparateConfigEnvVariableValue(version))
  }

  @ValueSource(strings = ["233.172", "232.8660", "232.8660.185"])
  @ParameterizedTest
  fun `separate config not supported`(version: String) {
    assertFalse(isJBCSeparateConfigSupported(version))
    assertNull(computeSeparateConfigEnvVariableValue(version))
    disableProcessPerConnection()
    assertNull(computeSeparateConfigEnvVariableValue(version))
  }

  @ValueSource(strings = ["232.8661", "233.173", "233.2349"])
  @ParameterizedTest
  fun `separate config supported but not enabled`(version: String) {
    assertTrue(isJBCSeparateConfigSupported(version))
    assertEquals("true", computeSeparateConfigEnvVariableValue(version))
    disableProcessPerConnection()
    assertEquals("false", computeSeparateConfigEnvVariableValue(version))
  }

  private fun disableProcessPerConnection() {
    Registry.get("rdct.enable.per.connection.client.process").setValue(false, disposable)
  }
}