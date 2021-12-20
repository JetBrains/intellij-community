// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class TestElement(val value: String) : AbstractCoroutineContextElement(TestElementKey)

internal object TestElementKey : CoroutineContext.Key<TestElement>

internal class TestElement2(val value: String) : AbstractCoroutineContextElement(TestElement2Key)

internal object TestElement2Key : CoroutineContext.Key<TestElement2>
