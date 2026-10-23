// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * IJPL-259452 The container never calls a constructor that [NonInjectable] marks.
 */
class NonInjectableConstructorTest {
  private val pluginDescriptor = DefaultPluginDescriptor("non-injectable-constructor-test")
  private val componentManager = TestComponentManager()

  @AfterEach
  fun disposeServices() {
    Disposer.dispose(componentManager.serviceParentDisposable)
  }

  @Test
  fun `an extension skips a non-injectable no-arg constructor and takes the scope constructor`() {
    val instance = componentManager.instantiateClass(NonInjectableNoArgAndScope::class.java, pluginDescriptor.pluginId)
    assertThat(instance.scope).isNotNull
  }

  @Test
  fun `a service skips a non-injectable no-arg constructor and takes the scope constructor`() {
    componentManager.registerService(NonInjectableNoArgAndScope::class.java, NonInjectableNoArgAndScope::class.java, pluginDescriptor, false)
    assertThat(componentManager.getService(NonInjectableNoArgAndScope::class.java)!!.scope).isNotNull
  }

  @Test
  fun `a light service skips a non-injectable no-arg constructor and takes the scope constructor`() {
    assertThat(componentManager.getService(NonInjectableNoArgAndScopeLightService::class.java)!!.scope).isNotNull
  }

  @Test
  fun `without the annotation an extension takes the no-arg constructor before the scope constructor`() {
    val instance = componentManager.instantiateClass(NoArgAndScope::class.java, pluginDescriptor.pluginId)
    assertThat(instance.scope).isNull()
  }

  @Test
  fun `without the annotation a service takes the no-arg constructor before the scope constructor`() {
    componentManager.registerService(NoArgAndScope::class.java, NoArgAndScope::class.java, pluginDescriptor, false)
    assertThat(componentManager.getService(NoArgAndScope::class.java)!!.scope).isNull()
  }

  @Test
  fun `a non-injectable constructor with other parameters does not change the choice`() {
    val instance = componentManager.instantiateClass(NonInjectableWithParametersAndNoArg::class.java, pluginDescriptor.pluginId)
    assertThat(instance.name).isEqualTo("container")
  }
}

private class NonInjectableNoArgAndScope {
  val scope: CoroutineScope?

  @NonInjectable
  constructor() {
    scope = null
  }

  @Suppress("unused")
  constructor(scope: CoroutineScope) {
    this.scope = scope
  }
}

@Service
private class NonInjectableNoArgAndScopeLightService {
  val scope: CoroutineScope?

  @NonInjectable
  constructor() {
    scope = null
  }

  @Suppress("unused")
  constructor(scope: CoroutineScope) {
    this.scope = scope
  }
}

private class NoArgAndScope {
  val scope: CoroutineScope?

  constructor() {
    scope = null
  }

  @Suppress("unused")
  constructor(scope: CoroutineScope) {
    this.scope = scope
  }
}

private class NonInjectableWithParametersAndNoArg @NonInjectable constructor(val name: String) {
  @Suppress("unused")
  constructor() : this("container")
}
