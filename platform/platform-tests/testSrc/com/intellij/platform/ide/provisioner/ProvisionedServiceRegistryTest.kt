// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.provisioner

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
internal class ProvisionedServiceRegistryTest {
  private val testDisposable = disposableFixture()

  @Test
  fun `falls through to the next extension`() {
    val descriptor = FakeServiceDescriptor(SERVICE_ID)
    maskRegistries(FakeRegistry(emptyMap()), FakeRegistry(mapOf(SERVICE_ID to descriptor)))

    assertSame(descriptor, ProvisionedServiceRegistry.getInstance().getServiceById(SERVICE_ID))
  }

  @Test
  fun `returns null when no extension recognizes the id`() {
    maskRegistries(FakeRegistry(mapOf("other" to FakeServiceDescriptor("other"))))

    assertNull(ProvisionedServiceRegistry.getInstance().getServiceById(SERVICE_ID))
  }

  @Test
  fun `falls back to the default service when no extension is registered`() {
    maskRegistries()

    assertNull(ProvisionedServiceRegistry.getInstance().getServiceById(SERVICE_ID), "the default registry recognizes nothing")
  }

  private fun maskRegistries(vararg registries: ProvisionedServiceRegistry) {
    ExtensionTestUtil.maskExtensions(ProvisionedServiceRegistry.EP, registries.asList(), testDisposable.get())
  }
}

private const val SERVICE_ID = "ai"

private class FakeRegistry(private val services: Map<String, ProvisionedServiceDescriptor>) : ProvisionedServiceRegistry {
  override fun getServiceById(id: String): ProvisionedServiceDescriptor? = services[id]
}

private class FakeServiceDescriptor(override val id: String) : ProvisionedServiceDescriptor {
  override val configurationFlow: Flow<ProvisionedServiceConfigurationResult> = emptyFlow()
}
