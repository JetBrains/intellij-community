// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule
import com.intellij.projectView.NestedFilesInProjectViewTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// TODO fix KTIJ-11594 without introducing KTIJ-27970
abstract class NestedFilesInProjectViewTest : BasePlatformTestCase() {
    private fun doTest(showMembers: Boolean, expectedTree: String) {
        myFixture.addFileToProject("Foo.kt", "class Foo {val foo = 0}")
        myFixture.addFileToProject("Bar.kt", "class Bar1 {val bar1 = 0}\nclass Bar2 {val bar2 = 0}")
        myFixture.addFileToProject("Foo.txt", "")
        myFixture.addFileToProject("Bar.txt", "")
        NestedFilesInProjectViewTest.doTest(myFixture, listOf(NestingRule(".kt", ".txt")), showMembers, expectedTree)
    }

    fun testWithoutMembers() = doTest(
        false,
        """
        |  -Bar.kt
        |   Bar.txt
        |  -Foo
        |   Foo.txt
        |""".trimMargin()
    )

    fun testWithMembers() = doTest(
        true,
        """
        |  -Bar.kt
        |   Bar.txt
        |   -Bar1
        |    bar1
        |   -Bar2
        |    bar2
        |  -Foo
        |   Foo.txt
        |   foo
        |""".trimMargin()
    )
}