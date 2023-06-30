// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.idea.fir.extensions.KtResolveExtensionProviderForTests
import org.jetbrains.kotlin.idea.fir.invalidateCaches

abstract class AbstractK2JvmBasicCompletionTestWithResolveExtension : AbstractK2JvmBasicCompletionTest() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("data.xml",
        """
            <xml>
                <package>generated.pckg</package>
                <function name = "aaaa"/>
                <function name = "bbbb"/>
                <class name = "CCCC">
                    <function name = "dddd"/>
                </class>
            </xml>
        """.trimIndent())
        project.extensionArea.getExtensionPoint(KtResolveExtensionProvider.EP_NAME)
            .registerExtension(KtResolveExtensionProviderForTests(), testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}