// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run

import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class K2RunConfigurationWithResolveTest : AbstractRunConfigurationWithResolveTest() {
    override fun isFirPlugin(): Boolean = true
}