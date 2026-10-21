// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import java.util.concurrent.Callable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TestElement(val value: String) : AbstractCoroutineContextElement(TestElementKey), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
}

object TestElementKey : CoroutineContext.Key<TestElement>

class TestElement2(val value: String) : AbstractCoroutineContextElement(TestElement2Key), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
}

object TestElement2Key : CoroutineContext.Key<TestElement2>

fun (() -> Unit).runnable(): Runnable = Runnable(this)

fun (() -> Unit).callable(): Callable<Unit> = Callable(this)
