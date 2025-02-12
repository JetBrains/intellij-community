// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.imports

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinStdJSProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractOptimizeImportsTest : AbstractImportsTest() {

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFile().toPath(),
            IgnoreTests.DIRECTIVES.IGNORE_K1,
            ".after",
            test = { super.doTest(unused) }
        )
    }

    override fun doTest(file: KtFile): String {
        OptimizedImportsBuilder.testLog = StringBuilder()
        try {
            val optimizer = KotlinImportOptimizer().processFile(file)
            optimizer.run()
            userNotificationInfo = optimizer.userNotificationInfo
            return OptimizedImportsBuilder.testLog.toString()
        } finally {
            OptimizedImportsBuilder.testLog = null
        }
    }

    override val nameCountToUseStarImportDefault: Int
        get() = Integer.MAX_VALUE
}

abstract class AbstractJvmOptimizeImportsTest : AbstractOptimizeImportsTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor =
        if (fileName().endsWith(".kts")) KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithScriptRuntime()
        else KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
}

abstract class AbstractJsOptimizeImportsTest : AbstractOptimizeImportsTest() {
    override fun getProjectDescriptor() = KotlinStdJSProjectDescriptor
}
