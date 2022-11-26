// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinJdkAndLibraryProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class AbstractJvmBasicCompletionTest : KotlinFixtureCompletionBaseTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : KotlinJdkAndLibraryProjectDescriptor(
      libraryFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance().libraryFiles,
      librarySourceFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance().librarySourceFiles,
    ) {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()
    }

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