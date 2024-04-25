// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.module

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.ProjectResource
import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.create
import com.intellij.testFramework.junit5.resources.providers.PathInfo
import com.intellij.testFramework.junit5.resources.providers.module.ModuleName
import com.intellij.testFramework.junit5.resources.providers.module.ModuleParams
import com.intellij.testFramework.junit5.resources.providers.module.ModulePersistenceType.Persistent
import com.intellij.testFramework.junit5.resources.providers.module.ModuleProvider
import com.intellij.testFramework.junit5.showcase.resources.ResourceCounter
import com.intellij.testFramework.junit5.showcase.resources.module.JUnit5ClassLevelModule.Companion.counter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * [Module] created on class-level. Since module needs project, set [ProjectResource].
 * [counter] ensures modules are disposed
 */
@TestApplication
@ProjectResource
class JUnit5ClassLevelModule {

  companion object {

    @Order(0)
    @JvmStatic
    @RegisterExtension
    val leak = AfterAllCallback {
      counter.ensureEmpty()
    }

    @JvmStatic
    @RegisterExtension
    @Order(1)
    val ext = ResourceExtensionApi.forProvider(ModuleProvider())

    private val counter = ResourceCounter()
  }


  @Test
  fun test(module1: Module, module2: Module) {
    Assertions.assertEquals(module1, module2, "Class level modules must be same")
    Assertions.assertFalse(module1.isDisposed)
  }

  @Test
  fun testManualDestroy(@TempDir tempDir: Path): Unit = runBlocking {
    val module = ext.create(ModuleParams(modulePersistenceType = Persistent { _, _ -> PathInfo(tempDir.resolve("module.iml")) }), disposeOnExit = false)
    Assertions.assertEquals(tempDir, module.moduleNioFile.parent)
    counter.acquire(module)
    writeAction {
      ModuleManager.getInstance(module.project).disposeModule(module)
    }
  }

  @Test
  fun testManualResource(): Unit = runBlocking {
    // Manually created projects are always different
    val m1 = ext.create()
    val m2 = ext.create()
    val m3 = ext.create(ModuleParams(ModuleName("abc123")))
    Assertions.assertNotEquals(m1, m2)
    Assertions.assertNotEquals(m1, m3)
  }
}