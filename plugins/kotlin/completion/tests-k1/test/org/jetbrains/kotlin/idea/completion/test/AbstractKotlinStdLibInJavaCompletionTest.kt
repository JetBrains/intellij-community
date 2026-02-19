// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class AbstractKotlinStdLibInJavaCompletionTest : KotlinFixtureCompletionBaseTestCase() {
    override fun getPlatform() = JvmPlatforms.unspecifiedJvmPlatform
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    override fun defaultCompletionType() = CompletionType.BASIC
}