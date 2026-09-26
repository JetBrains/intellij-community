// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import java.util.concurrent.Callable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class TestElement(val value: String) : AbstractCoroutineContextElement(TestElementKey), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
}

internal object TestElementKey : CoroutineContext.Key<TestElement>

internal class TestElement2(val value: String) : AbstractCoroutineContextElement(TestElement2Key), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
}

internal object TestElement2Key : CoroutineContext.Key<TestElement2>

internal fun (() -> Unit).runnable(): Runnable = Runnable(this)

internal fun (() -> Unit).callable(): Callable<Unit> = Callable(this)
