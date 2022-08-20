// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import java.io.File

interface LazyFileOutputProducer<I : Any, C> {
    fun isUpToDate(input: I): Boolean
    fun lazyProduceOutput(input: I, computationContext: C): List<File>
}
