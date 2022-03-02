// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.index

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import org.jetbrains.kotlin.idea.caches.project.ModuleProductionSourceScope
import org.jetbrains.kotlin.idea.stubindex.SubpackagesIndexService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope

private const val REGISTRY_CACHE_KEY = "kotlin.cache.top.level.subpackages"

class SubpackageIndexServiceTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testBasicWithoutCaching() {
        Registry.get(REGISTRY_CACHE_KEY).withValue(tempValue = false) {
            setupSimpleTest()
            basicSimpleTest()
        }
    }

    fun testBasicWithCaching() {
        Registry.get(REGISTRY_CACHE_KEY).withValue(tempValue = true) {
            setupSimpleTest()
            basicSimpleTest()
            basicSimpleTest()
        }
    }

    private fun basicSimpleTest() {
        val subpackagesIndex = SubpackagesIndexService.getInstance(project)
        val fqName1 = FqName("foo")
        val fqName2 = fqName1.child(Name.identifier("bar"))
        val fqName30 = fqName2.child(Name.identifier("zoo"))
        val fqName31 = fqName2.child(Name.identifier("moo"))

        listOf(FqName.ROOT, fqName1, fqName2, fqName30, fqName31).forEach {
            assertTrue("fqName `${it.asString()}` should exist", subpackagesIndex.packageExists(it))
        }

        val scope = ModuleProductionSourceScope(module)

        listOf(FqName.ROOT, fqName1, fqName2, fqName30, fqName31).forEach {
            assertTrue("fqName `${it.asString()}` should exist", subpackagesIndex.packageExists(it, scope))
        }

        listOf(fqName2.child(Name.identifier("doo")), FqName("a")).forEach {
            assertFalse("fqName `${it.asString()}` shouldn't exist", subpackagesIndex.packageExists(it, scope))
        }

        assertEquals(listOf(fqName1), subpackagesIndex.getSubpackages(FqName.ROOT, scope, MemberScope.ALL_NAME_FILTER))
        assertEquals(listOf(fqName2), subpackagesIndex.getSubpackages(fqName1, scope, MemberScope.ALL_NAME_FILTER))
        assertEquals(listOf(fqName31, fqName30), subpackagesIndex.getSubpackages(fqName2, scope, MemberScope.ALL_NAME_FILTER)
            .sortedBy(FqName::asString))

        listOf(fqName31, fqName30, fqName31.child(Name.identifier("a")), fqName30.child(Name.identifier("a"))).forEach {
            assertTrue(subpackagesIndex.getSubpackages(it, scope, MemberScope.ALL_NAME_FILTER).isEmpty())
        }
    }

    private fun setupSimpleTest() {
        myFixture.configureByText("1.kt", "package foo.bar.zoo")
        myFixture.configureByText("2.kt", "package foo.bar.moo")
    }

}