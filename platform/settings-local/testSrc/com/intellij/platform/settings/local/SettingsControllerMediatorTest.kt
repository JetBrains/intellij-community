// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.*
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class SettingsControllerMediatorTest {
  @Test
  fun `inapplicable - resolved`() {
    withControllers(listOf(
      createGet { GetResult.inapplicable() },
      createGet { GetResult.resolved("hi") },
      createGet { error("must not be called") },
    )) {
      val mediator = SettingsControllerMediator()
      assertThat(mediator.getItem(key("foo"))).isEqualTo("hi")
    }
  }

  @Test
  fun `inapplicable - resolved as null`() {
    withControllers(listOf(
      createGet { GetResult.inapplicable() },
      createGet { GetResult.resolved(null) },
      createGet { error("must not be called") },
    )) {
      val mediator = SettingsControllerMediator()
      assertThat(mediator.getItem(key("foo"))).isNull()
    }
  }

  @Test
  fun `resolved as null - another resolved (that must be ignored)`() {
    withControllers(listOf(
      createGet { GetResult.resolved(null) },
      createGet { error("must not be called") },
    )) {
      val mediator = SettingsControllerMediator()
      assertThat(mediator.getItem(key("foo"))).isNull()
    }
  }
}

private fun withControllers(list: List<DelegatedSettingsController>, task: () -> Unit) {
  val point = SETTINGS_CONTROLLER_EP_NAME.point as ExtensionPointImpl<DelegatedSettingsController>
  val disposable = Disposer.newDisposable()
  try {
    point.maskAll(newList = list, parentDisposable = disposable, fireEvents = false)
    task()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun createGet(resultSupplier: () -> GetResult<String>): DelegatedSettingsController {
  return object : DelegatedSettingsController {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
      return resultSupplier() as GetResult<T?>
    }

    override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): Boolean {
      TODO("Not yet implemented")
    }

    @IntellijInternalApi
    override fun <T : Any> hasKeyStartsWith(key: SettingDescriptor<T>): Boolean? {
      TODO("Not yet implemented")
    }
  }
}

private fun key(name: String): SettingDescriptor<String> = settingDescriptor(name, PluginManagerCore.CORE_ID, StringSettingSerializerDescriptor) { }