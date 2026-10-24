// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ServiceContainerUtilTest {
  @TestDisposable
  private lateinit var testDisposable: Disposable

  private val application get() = ApplicationManager.getApplication()

  @Test
  fun `an absent service is absent again after the dispose`() {
    val replacement = childOfTest()
    val fake = object : AbsentService {}

    application.registerOrReplaceServiceInstance(AbsentService::class.java, fake, replacement)
    assertThat(application.getService(AbsentService::class.java)).isSameAs(fake)

    Disposer.dispose(replacement)
    assertThat(application.getService(AbsentService::class.java)).isNull()
  }

  @Test
  fun `a registration that nobody created stays uncreated and comes back as the production service`() {
    registerProductionService()
    val replacement = childOfTest()
    val fake = object : RegisteredService {}

    application.registerOrReplaceServiceInstance(RegisteredService::class.java, fake, replacement)
    assertThat(application.getService(RegisteredService::class.java)).isSameAs(fake)

    Disposer.dispose(replacement)
    assertThat(application.getServiceIfCreated(RegisteredService::class.java)).isNull()
    assertThat(application.getService(RegisteredService::class.java)).isInstanceOf(ProductionRegisteredService::class.java)
  }

  @Test
  fun `a disposable fake of an absent service is disposed and absent after the dispose`() {
    val replacement = childOfTest()
    val fake = DisposableAbsentService()

    application.registerOrReplaceServiceInstance(AbsentService::class.java, fake, replacement)
    assertThat(application.getService(AbsentService::class.java)).isSameAs(fake)

    Disposer.dispose(replacement)
    assertThat(fake.disposed).isTrue()
    assertThat(application.getService(AbsentService::class.java)).isNull()
  }

  @Test
  fun `a replaced service instance is restored after the dispose`() {
    registerProductionService()
    val production = application.getService(RegisteredService::class.java)
    val replacement = childOfTest()
    val fake = object : RegisteredService {}

    application.registerOrReplaceServiceInstance(RegisteredService::class.java, fake, replacement)
    assertThat(application.getService(RegisteredService::class.java)).isSameAs(fake)

    Disposer.dispose(replacement)
    assertThat(application.getService(RegisteredService::class.java)).isSameAs(production)
  }

  /** A disposable for one replacement. The test disposable ends it when an assertion fails first. */
  private fun childOfTest(): Disposable = Disposer.newDisposable(testDisposable, "service replacement")

  /** Registers [ProductionRegisteredService] lazily, as a plugin descriptor does, until the end of the test. */
  private fun registerProductionService() {
    val container = application as ComponentManagerEx
    container.registerService(
      RegisteredService::class.java,
      ProductionRegisteredService::class.java,
      DefaultPluginDescriptor("ServiceContainerUtilTest"),
      false,
    )
    Disposer.register(testDisposable) {
      container.unregisterService(RegisteredService::class.java)
    }
  }
}

internal interface AbsentService

internal interface RegisteredService

internal class ProductionRegisteredService : RegisteredService

internal class DisposableAbsentService : AbsentService, Disposable {
  var disposed: Boolean = false

  override fun dispose() {
    disposed = true
  }
}
