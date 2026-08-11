// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.testFramework.junit5.EnvValue
import com.intellij.util.EnvironmentUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


internal class JUnit5EnvVarTest {
  private companion object {
    private const val ENV_NAME = "SOME_KEY_THAT_DOESNT_EXIST"
  }

  @EnvValue(ENV_NAME, "123")
  @Test
  fun testWithVar() {
    Assertions.assertEquals("123", EnvironmentUtil.getValue(ENV_NAME))
    Assertions.assertNotNull(PathEnvironmentVariableUtil.getPathVariableValue())
  }

  @Test
  fun testNoVar() {
    Assertions.assertNull(EnvironmentUtil.getValue(ENV_NAME))
    Assertions.assertNotNull(PathEnvironmentVariableUtil.getPathVariableValue())
  }
}
