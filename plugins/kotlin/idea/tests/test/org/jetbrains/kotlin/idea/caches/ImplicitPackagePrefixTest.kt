// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class ImplicitPackagePrefixTest : KotlinLightCodeInsightFixtureTestCase() {
    private lateinit var cacheService: PerModulePackageCacheService

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    override fun setUp() {
        super.setUp()
        cacheService = PerModulePackageCacheService.getInstance(myFixture.project)
    }

    private fun prefix(): String {
        return cacheService.getImplicitPackagePrefix(myFixture.module.sourceRoots[0]).asString()
    }

    fun testSimple() {
        myFixture.configureByText("foo.kt", "package com.example.foo")
        assertEquals("com.example.foo", prefix())
    }

    fun testAmbiguous() {
        myFixture.configureByText("foo.kt", "package com.example.foo")
        myFixture.configureByText("bar.kt", "package com.example.bar")
        assertEquals("", prefix())
    }

    fun testNestedChild() {
        myFixture.tempDirFixture.createFile("foo/foo.kt", "package com.example.foo")
        assertEquals("com.example", prefix())
    }

    fun testDeeplyNestedChild() {
        myFixture.tempDirFixture.createFile("somelib/model/domainA/foo.kt", "package org.somename.somelib.model.domainA")
        myFixture.tempDirFixture.createFile("somelib/model/domainB/foo.kt", "package org.somename.somelib.model.domainB")
        myFixture.tempDirFixture.createFile("somelib/services/layer1/foo.kt", "package org.somename.somelib.services.layer1")
        myFixture.tempDirFixture.createFile("somelib/services/layer2/foo.kt", "package org.somename.somelib.services.layer2")
        assertEquals("org.somename", prefix())
    }

    fun testUpdateOnCreate() {
        myFixture.configureByText("foo.kt", "package com.example.foo")
        assertEquals("com.example.foo", prefix())
        myFixture.configureByText("bar.kt", "package com.example.bar")
        assertEquals("", prefix())
    }

    fun testUpdateOnDelete() {
        myFixture.configureByText("foo.kt", "package com.example.foo")
        val bar = myFixture.configureByText("bar.kt", "package com.example.bar")
        assertEquals("", prefix())
        myFixture.project.executeWriteCommand("") {
            bar.delete()
        }
        assertEquals("com.example.foo", prefix())
    }

    fun testUpdateOnChangeContent() {
        val foo = myFixture.configureByText("foo.kt", "package com.example.foo")
        assertEquals("com.example.foo", prefix())
        myFixture.project.executeWriteCommand("") {
            foo.viewProvider.document!!.let {
                it.replaceString(0, it.textLength, "package com.example.bar")
                PsiDocumentManager.getInstance(project).commitDocument(it)
            }
        }
        assertEquals("com.example.bar", prefix())
    }
}
