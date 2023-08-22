// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.idea.fir.extensions.KtResolveExtensionProviderForTests
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class AbstractK2ReferenceResolveWithResolveExtensionTest : AbstractFirReferenceResolveTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("data.xml", """
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
}
