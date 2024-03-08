// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinStdJSLegacyCombinedJarProjectDescriptor

abstract class AbstractK2JsBasicCompletionLegacyStdlibTest : AbstractK2JsBasicCompletionTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinStdJSLegacyCombinedJarProjectDescriptor
    }
}
