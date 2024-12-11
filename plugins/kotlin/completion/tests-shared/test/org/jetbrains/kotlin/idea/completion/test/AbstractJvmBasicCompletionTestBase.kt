// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class AbstractJvmBasicCompletionTestBase : KotlinFixtureCompletionBaseTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinJdkAndLibraryProjectDescriptorOnJdk18

    override fun getPlatform() = JvmPlatforms.jvm8
    override fun defaultCompletionType() = CompletionType.BASIC

    override fun configureFixture(testPath: String) {
        // those classes are missing in mockJDK-1.8
        with(myFixture) {
            addCharacterCodingException()
            addAppendable()
            addHashSet()
            addLinkedHashSet()
            addNoSuchAlgorithmException()
            addUnknownHostException()
            addSocket()
            addSwingUtilities()
            addSqlStatement()
            addSqlArray()
            addSqlBlob()
            addSqlDate()
            addUrlConnection()
        }

        super.configureFixture(testPath)
    }
}

private object KotlinJdkAndLibraryProjectDescriptorOnJdk18 : KotlinWithJdkAndRuntimeLightProjectDescriptor() {

    override fun addDefaultLibraries(model: ModifiableRootModel) {
        // Skip adding JetBrains annotation for completion tests
    }
}