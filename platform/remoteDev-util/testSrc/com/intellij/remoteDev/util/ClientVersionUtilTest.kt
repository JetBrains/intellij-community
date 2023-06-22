package com.intellij.remoteDev.util

import org.junit.Assert
import org.junit.Test

class ClientVersionUtilTest {
  @Test
  fun `233_173 is supported`() {
    Assert.assertTrue(ClientVersionUtil.isJBCSeparateConfigSupported("233.173"))
  }

  @Test
  fun `233_172 is not supported`() {
    Assert.assertFalse(ClientVersionUtil.isJBCSeparateConfigSupported("233.172"))
  }

  @Test
  fun `232_9999 is not supported`() {
    Assert.assertFalse(ClientVersionUtil.isJBCSeparateConfigSupported("232.9999"))
  }

  @Test
  fun `241_1 is supported`() {
    Assert.assertTrue(ClientVersionUtil.isJBCSeparateConfigSupported("241.1"))
  }
}