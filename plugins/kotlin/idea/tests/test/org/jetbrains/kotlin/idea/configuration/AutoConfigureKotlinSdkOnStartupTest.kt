// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

// Do not add new tests here since application is initialized only once
@RunWith(JUnit38ClassRunner::class)
class AutoConfigureKotlinSdkOnStartupTest : AbstractConfigureKotlinInTempDirTest() {
    fun testKotlinSdkAdded() {
        Assert.assertTrue(runReadAction { ProjectJdkTable.getInstance() }.allJdks.any { it.sdkType is KotlinSdkType })
    }
}