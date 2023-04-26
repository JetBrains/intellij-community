// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment

import com.intellij.ide.environment.impl.EnvironmentKeyStubGenerator
import com.intellij.ide.environment.impl.EnvironmentUtil
import com.intellij.ide.environment.impl.HeadlessEnvironmentService
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
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class HeadlessEnvironmentServiceTest : LightPlatformTestCase() {
  private val configurationFilePath : Path
    get() = Path.of(project.basePath!! + "/environmentKeys.json")

  private suspend fun getExistingKey(key: EnvironmentKey): String {
    val value1 = service<EnvironmentService>().getValue(key, null)
    val value2 = service<EnvironmentService>().getValue(key, undefined)
    TestCase.assertEquals(value1!!, value2!!)
    return value1
  }

  private fun runTestWithMaskedServices(action: suspend () -> Unit) {
    ExtensionTestUtil.maskExtensions(EnvironmentKeyProvider.EP_NAME, listOf(TestKeyProvider()), testRootDisposable)
    runBlocking {
      val job = SupervisorJob()
      val service = HeadlessEnvironmentService(CoroutineScope(coroutineContext + job))
      ApplicationManager.getApplication().replaceService(EnvironmentService::class.java, service, testRootDisposable)
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
      assertEquals(
"""[
  {
    "description": [
      "My dummy test key",
      "With a long description"
    ],
    "key": "my.dummy.test.key",
    "value": ""
  },
  {
    "description": [
      "Just another dummy key"
    ],
    "key": "my.dummy.test.key.2",
    "value": ""
  }
]""", contents)

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
    "key": "my.dummy.test.key.2",
    "value": ""
  }
]""", contents)

      checkGeneratedFileSanity()
    } finally {
      configurationFilePath.deleteIfExists()
    }
  }

  private fun runTestWithFile(text: String, action: suspend () -> Unit) = runTestWithMaskedServices {
    blockingContext {
      runWriteAction {
        configurationFilePath.write(text)
      }
    }
    EnvironmentUtil.setPathTemporarily(configurationFilePath, testRootDisposable)
    try {
      action()
    } finally {
      configurationFilePath.deleteIfExists()
    }
  }

  fun testCheckExistingKey() = runTestWithFile(
    """[
  {
    "description": [
      "My dummy test key",
      "With a long description"
    ],
    "key": "my.dummy.test.key",
    "value": "secret-value"
  }
]""") {
    val dummyKeyValue = getExistingKey(dummyKey)
    assertEquals("secret-value", dummyKeyValue)
  }

  fun testAbsentKey() = runTestWithFile(
    """[
  {
    "description": [
      "My dummy test key",
      "With a long description"
    ],
    "key": "my.dummy.test.key",
    "value": ""
  }
]""") {
    try {
      TestCase.assertEquals(undefined, service<EnvironmentService>().getValue(dummyKey, undefined))
      service<EnvironmentService>().getValue(dummyKey, null)
      fail("should throw")
    } catch (e : HeadlessEnvironmentService.MissingEnvironmentKeyException) {
      // ignored, we expect this outcome
    }
  }

  fun testUnknownKey() = runTestWithFile("""[
  {
    "description": [
      "My dummy test key",
      "With a long description"
    ],
    "key": "my.dummy.test.key",
    "value": ""
  }
]""") {
    try {
      service<EnvironmentService>().getValue(notRegisteredDummyKey, undefined)
      // the warning in log is intentional
      fail("should throw")
    } catch (e : AssertionError) {
      // ignored, we expect this outcome
    }
  }

  companion object {
    private const val undefined = "__undefined__"
  }
}

private val dummyKey: EnvironmentKey = EnvironmentKey.create("my.dummy.test.key")
private val dummyKey2: EnvironmentKey = EnvironmentKey.create("my.dummy.test.key.2")
private val notRegisteredDummyKey: EnvironmentKey = EnvironmentKey.create("not.registered.dummy.key")

private class TestKeyProvider : EnvironmentKeyProvider {

  override fun getKnownKeys(): Map<EnvironmentKey, String> = mapOf(
    dummyKey to "My dummy test key\nWith a long description",
    dummyKey2 to "Just another dummy key",
  )

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> =
    emptyList()
}