// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.run

import org.jetbrains.kotlin.idea.run.AbstractRunConfigurationTest
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class K2RunConfigurationTest  : AbstractRunConfigurationTest(){
    override fun isFirPlugin(): Boolean = true
}