// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment

import com.intellij.ide.environment.impl.EnvironmentKeyStubGenerator
import com.intellij.ide.environment.impl.EnvironmentUtil
import com.intellij.ide.environment.impl.HeadlessEnvironmentParametersService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.io.readText
import com.intellij.util.io.write
import junit.framework.TestCase
import junit.framework.TestCase.*
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class HeadlessEnvironmentParametersServiceTest : LightPlatformTestCase() {

  private val configurationFilePath : Path
    get() = Path.of(project.basePath!! + "/environmentKeys.json")

  private suspend fun getExistingKey(key: EnvironmentKey): String {
    val value1 = service<EnvironmentParametersService>().requestEnvironmentValue(key)
    val value2 = service<EnvironmentParametersService>().getEnvironmentValueOrNull(key)
    TestCase.assertEquals(value1!!, value2)
    return value1
  }

  private fun runTestWithMaskedServices(action: suspend () -> Unit) {
    ExtensionTestUtil.maskExtensions(EnvironmentKeyRegistry.EP_NAME, listOf(TestKeyRegistry()), testRootDisposable)
    runBlocking {
      val job = SupervisorJob()
      val service = HeadlessEnvironmentParametersService(CoroutineScope(coroutineContext + job))
      ApplicationManager.getApplication().replaceService(EnvironmentParametersService::class.java, service, testRootDisposable)
      action()
      job.cancel()
    }
  }

  private suspend fun checkGeneratedFileSanity() {
    EnvironmentUtil.setPathTemporarily(configurationFilePath, testRootDisposable)
    val file = VfsUtil.findFile(configurationFilePath, true)!!
    val text = file.readText().replace("\"\"", "\"a-value\"")
    blockingContext {
      runWriteAction {
        file.writeText(text)
      }
    }
    val value = getExistingKey(dummyKey)
    assertEquals("a-value", value)
  }

  fun testCheckGeneratedJson() = runTestWithMaskedServices {
    try {
      EnvironmentKeyStubGenerator().performGeneration(listOf("--file=$configurationFilePath"))

      val contents = configurationFilePath.readText()
      assertEquals(getJsonContents(null, dummyKeyWithDefaultValue.defaultValue), contents)

      checkGeneratedFileSanity()
    } finally {
      configurationFilePath.deleteIfExists()
    }
  }

  fun testCheckGeneratedJsonWithoutDescriptions() = runTestWithMaskedServices {
    try {
      EnvironmentKeyStubGenerator().performGeneration(listOf("--file=$configurationFilePath", "--no-descriptions"))

      val contents = configurationFilePath.readText()
      assertEquals(
"""[
  {
    "key": "my.dummy.test.key",
    "value": ""
  },
  {
    "key": "my.dummy.test.key.with.default.value",
    "value": "Foo"
  }
]""", contents)

      checkGeneratedFileSanity()
    } finally {
      configurationFilePath.deleteIfExists()
    }
  }

  private fun runTestWithValues(value1: String?, value2: String?, action: suspend () -> Unit) = runTestWithMaskedServices {
    blockingContext {
      runWriteAction {
        configurationFilePath.write(getJsonContents(value1, value2))
      }
    }
    EnvironmentUtil.setPathTemporarily(configurationFilePath, testRootDisposable)
    try {
      action()
    } finally {
      configurationFilePath.deleteIfExists()
    }
  }

  fun testCheckExistingKey() = runTestWithValues("secret-value", null) {
    val dummyKeyValue = getExistingKey(dummyKey)
    assertEquals("secret-value", dummyKeyValue)
  }

  fun testAbsentKey() = runTestWithValues(null, null) {
    try {
      assertNull(service<EnvironmentParametersService>().getEnvironmentValueOrNull(dummyKey))
      service<EnvironmentParametersService>().requestEnvironmentValue(dummyKey)
      fail("should throw")
    } catch (e : HeadlessEnvironmentParametersService.MissingEnvironmentKeyException) {
      // ignored
    }
  }

  fun testDefaultKey() = runTestWithValues(null, null) {
    val defaultedDummyKeyValue = getExistingKey(dummyKeyWithDefaultValue)
    assertEquals("Foo", defaultedDummyKeyValue)
  }

  fun testOverwrittenDefaultKey() = runTestWithValues(null, "Bar") {
    val defaultedDummyKeyValue = getExistingKey(dummyKeyWithDefaultValue)
    assertEquals("Bar", defaultedDummyKeyValue)
  }

  fun testUnknownKey() = runTestWithValues(null, null) {
    try {
      assertNull(service<EnvironmentParametersService>().getEnvironmentValueOrNull(dummyKey))
      service<EnvironmentParametersService>().requestEnvironmentValue(notRegisteredDummyKey)
      // the warning in log is intentional
      fail("should throw")
    } catch (e : HeadlessEnvironmentParametersService.MissingEnvironmentKeyException) {
      // ignored
    }
  }
}

private val dummyKey: EnvironmentKey = EnvironmentKey.createKey("my.dummy.test.key", { "My dummy test key\nWith a long description" })
private val notRegisteredDummyKey: EnvironmentKey = EnvironmentKey.createKey("not.registered.dummy.key", { "My dummy test key" })
private val dummyKeyWithDefaultValue: EnvironmentKey = EnvironmentKey.createKey("my.dummy.test.key.with.default.value", { "My dummy test key with default value"}, "Foo")

fun getJsonContents(value1: String?, value2: String?) : String =
"""[
  {
    "description": [
      "My dummy test key",
      "With a long description"
    ],
    "key": "my.dummy.test.key",
    "value": "${value1 ?: ""}"
  },
  {
    "description": [
      "My dummy test key with default value"
    ],
    "key": "my.dummy.test.key.with.default.value",
    "value": "${value2 ?: ""}"
  }
]"""

private class TestKeyRegistry : EnvironmentKeyRegistry {

  override fun getAllKeys(): List<EnvironmentKey> = listOf(
    dummyKey,
    dummyKeyWithDefaultValue
  )

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> =
    emptyList()
}