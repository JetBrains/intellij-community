// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.index

import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope

class SubpackageIndexServiceTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testBasicWithoutCaching() {
        setupSimpleTest()
        basicSimpleTest()
    }

    fun testBasicWithCaching() {
        setupSimpleTest()
        basicSimpleTest()
        basicSimpleTest()
    }

    private fun basicSimpleTest() {
        val fqName1 = FqName("foo")
        val fqName2 = fqName1.child(Name.identifier("bar"))
        val fqName30 = fqName2.child(Name.identifier("zoo"))
        val fqName31 = fqName2.child(Name.identifier("moo"))

        listOf(FqName.ROOT, fqName1, fqName2, fqName30, fqName31).forEach {
            assertTrue("fqName `${it.asString()}` should exist", PackageIndexUtil.packageExists(it, project))
        }

        val scope = module.moduleProductionSourceScope

        listOf(FqName.ROOT, fqName1, fqName2, fqName30, fqName31).forEach {
            assertTrue("fqName `${it.asString()}` should exist", PackageIndexUtil.packageExists(it, scope))
        }

        listOf(fqName2.child(Name.identifier("doo")), FqName("a")).forEach {
            assertFalse("fqName `${it.asString()}` shouldn't exist", PackageIndexUtil.packageExists(it, scope))
        }

        assertSameElements(listOf(fqName1), PackageIndexUtil.getSubpackages(FqName.ROOT, scope, MemberScope.ALL_NAME_FILTER))
        assertSameElements(listOf(fqName2), PackageIndexUtil.getSubpackages(fqName1, scope, MemberScope.ALL_NAME_FILTER))
        assertSameElements(listOf(fqName31, fqName30), PackageIndexUtil.getSubpackages(fqName2, scope, MemberScope.ALL_NAME_FILTER)
            .sortedBy(FqName::asString))

        listOf(fqName31, fqName30, fqName31.child(Name.identifier("a")), fqName30.child(Name.identifier("a"))).forEach {
            assertTrue(PackageIndexUtil.getSubpackages(it, scope, MemberScope.ALL_NAME_FILTER).isEmpty())
        }
    }

    private fun setupSimpleTest() {
        myFixture.configureByText("1.kt", "package foo.bar.zoo")
        myFixture.configureByText("2.kt", "package foo.bar.moo")
    }

}