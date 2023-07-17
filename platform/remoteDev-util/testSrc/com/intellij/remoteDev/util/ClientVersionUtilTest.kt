package com.intellij.remoteDev.util

import com.intellij.remoteDev.util.ClientVersionUtil.isJBCSeparateConfigSupported
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ClientVersionUtilTest {
  @ValueSource(strings = ["233.173", "233.SNAPSHOT", "241.1", "241.SNAPSHOT"])
  @ParameterizedTest
  fun `supported version`(version: String) {
    assertTrue(isJBCSeparateConfigSupported(version))
  }

  @ValueSource(strings = ["233.172", "232.SNAPSHOT", "232.9999"])
  @ParameterizedTest
  fun `not supported versions`(version: String) {
    assertFalse(isJBCSeparateConfigSupported(version))
  }
}