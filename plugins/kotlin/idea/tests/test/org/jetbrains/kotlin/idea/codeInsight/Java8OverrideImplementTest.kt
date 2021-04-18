// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class OldJava8OverrideImplementTest : Java8OverrideImplementTest<OverrideMemberChooserObject>(), OldOverrideImplementTestMixIn

abstract class Java8OverrideImplementTest<T : ClassMember> : AbstractOverrideImplementTest<T>() {
    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("codeInsight/overrideImplement/jdk8")

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK

    fun testOverrideCollectionStream() = doOverrideFileTest("stream")
}
