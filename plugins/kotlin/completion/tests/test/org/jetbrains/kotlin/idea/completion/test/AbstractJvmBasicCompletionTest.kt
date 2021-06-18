// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        libraryFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE.libraryFiles,
        librarySourceFiles = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE.librarySourceFiles,
    ) {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk16()
    }

    override fun getPlatform() = JvmPlatforms.jvm16
    override fun defaultCompletionType() = CompletionType.BASIC

    override fun configureFixture(testPath: String) {
        // those classes are missing in mockJDK-1.7
        with(myFixture) {
            addCharacterCodingException()
            addAppendable()
            addHashSet()
            addLinkedHashSet()
        }

        super.configureFixture(testPath)
    }
}