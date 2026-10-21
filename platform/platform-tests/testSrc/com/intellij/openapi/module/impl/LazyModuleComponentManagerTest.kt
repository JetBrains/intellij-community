// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.isEmpty
import com.intellij.serviceContainer.precomputeModuleLevelExtensionModel
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.registerServiceInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

private val TEST_KEY = Key.create<String>("LazyModuleComponentManagerTest")

@TestApplication
class LazyModuleComponentManagerTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture()

  @BeforeEach
  fun checkNoModuleLevelElements() {
    assumeTrue(precomputeModuleLevelExtensionModel().isEmpty, "a plugin on the test classpath declares module-level elements")
  }

  @Test
  fun `module without module-level elements has no container`() {
    val module = moduleFixture.get()

    module.putUserData(TEST_KEY, "value")

    assertThat(module.getUserData(TEST_KEY)).isEqualTo("value")
    assertThat(module.getService(TestModuleService::class.java)).isNull()
    assertThat(module.isDisposed).isFalse()
    assertThat(containerOf(module)).isNull()
  }

  @Test
  fun `module disposal disposes children without a container`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get())
    val module = edtWriteAction { manager.newNonPersistentModule("disposed", "") }
    val child = Disposer.newCheckedDisposable()
    Disposer.register(module, child)

    edtWriteAction { manager.disposeModule(module) }

    assertThat(child.isDisposed).isTrue()
    assertThat(module.isDisposed).isTrue()
    assertThat(containerOf(module)).isNull()
  }

  @Test
  fun `service registration creates the container`() {
    val module = moduleFixture.get()
    val service = TestModuleService()

    module.registerServiceInstance(TestModuleService::class.java, service)

    assertThat(containerOf(module)).isNotNull()
    assertThat(module.getService(TestModuleService::class.java)).isSameAs(service)
  }

  @Test
  fun `module disposal disposes the created container`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get())
    val module = edtWriteAction { manager.newNonPersistentModule("disposedWithContainer", "") }
    module.registerServiceInstance(TestModuleService::class.java, TestModuleService())
    val container = containerOf(module)!!

    edtWriteAction { manager.disposeModule(module) }

    assertThat(container.isDisposed).isTrue()
    assertThat(module.isDisposed).isTrue()
  }

  @Test
  fun `module without a container returns a project service with an error`(@TestDisposable disposable: Disposable) {
    val module = moduleFixture.get()
    val service = TestProjectService()
    projectFixture.get().registerOrReplaceServiceInstance(TestProjectService::class.java, service, disposable)

    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThat(module.getService(TestProjectService::class.java)).isSameAs(service)
    }

    assertThat(error).hasMessageContaining("is registered as project service, but requested as module one")
    assertThat(containerOf(module)).isNull()
  }

  @Test
  fun `module dropped by a failed init leaves no node in the Disposer tree`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get()) as ModuleManagerBridgeImpl
    // a model other than the shared empty instance makes the module create its container at init
    val model = PrecomputedExtensionModel(extensionPoints = emptyList(), nameToExtensions = emptyMap(), services = emptyList())
    var dropped: ModuleBridge? = null
    // the steps of ModuleManagerBridgeImpl.createModuleBridges: init throws, and the caller drops the module without disposal
    try {
      manager.createModule(ModuleId("dropped"), "dropped", null, manager.entityStore, null) { module ->
        dropped = module
        module.initServiceContainer(model)
        throw IOException("init fails")
      }
    }
    catch (_: IOException) {
    }

    val droppedModule = dropped!!
    try {
      Disposer.getTree().assertNoReferenceKeptInTree((droppedModule as ComponentManagerEx).getDisposableDelegate())
    }
    finally {
      edtWriteAction { Disposer.dispose(droppedModule) }
    }
  }

  @Test
  fun `child registered before the container is disposed before the module services`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get())
    val module = edtWriteAction { manager.newNonPersistentModule("childBeforeContainer", "") }
    val service = TestModuleService()
    var seenByChild: Result<TestModuleService?>? = null
    Disposer.register(module, Disposable { seenByChild = runCatching { module.getService(TestModuleService::class.java) } })
    module.registerServiceInstance(TestModuleService::class.java, service)

    edtWriteAction { manager.disposeModule(module) }

    assertThat(seenByChild).isEqualTo(Result.success(service))
  }

  @Test
  fun `child registered after the container is disposed before the module services`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get())
    val module = edtWriteAction { manager.newNonPersistentModule("childAfterContainer", "") }
    val service = TestModuleService()
    var seenByChild: Result<TestModuleService?>? = null
    module.registerServiceInstance(TestModuleService::class.java, service)
    Disposer.register(module, Disposable { seenByChild = runCatching { module.getService(TestModuleService::class.java) } })

    edtWriteAction { manager.disposeModule(module) }

    assertThat(seenByChild).isEqualTo(Result.success(service))
  }

  @Test
  fun `disposed module does not create a container`(): Unit = timeoutRunBlocking {
    val manager = ModuleManager.getInstance(projectFixture.get())
    val module = edtWriteAction { manager.newNonPersistentModule("disposedBeforeContainer", "") }

    edtWriteAction { manager.disposeModule(module) }

    assertThrows<AlreadyDisposedException> { (module as ComponentManagerEx).getMutableComponentContainer() }
    assertThat(containerOf(module)).isNull()
  }

  private fun containerOf(module: Module) = (module as ComponentManagerEx).getMutableComponentContainerIfCreated()

  private class TestModuleService

  private class TestProjectService
}
