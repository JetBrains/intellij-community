// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames

class KotlinClassNameSuggesterTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K2

    fun testTopLevelClass() = test("x/y/Foo", "foo")
    fun testJavaLangString() = test("java/lang/String", "string")
    fun testKotlinList() = test("kotlin/collections/List", "list")
    fun testNestedClass() = test("x/y/Foo.Bar.Baz", "baz", "barBaz", "fooBarBaz")
    fun testDefaultPackageTopLevelClass() = test("Foo", "foo")
    fun testDefaultPackageNestedClass() = test("Foo.Bar.Baz", "baz", "barBaz", "fooBarBaz")
    fun testAnonymousClass() = test(ClassId(FqName.ROOT, FqName.topLevel(SpecialNames.ANONYMOUS), true), "anonymous")
    fun testCompoundName() = test("FooBarBaz", "baz", "barBaz", "fooBarBaz")
    fun testNestedCompoundName() = test("FooBar.BazBoo", "boo", "bazBoo", "fooBarBazBoo")

    private fun test(classIdString: String, vararg names: String) {
        test(ClassId.fromString(classIdString), *names)
    }

    private fun test(classId: ClassId, vararg names: String) {
        val nameSuggester = KotlinNameSuggester()
        val actualNames = nameSuggester.suggestClassNames(classId).toList().sorted()
        TestCase.assertEquals(names.sorted(), actualNames)
    }

    override fun getTestDataPath() = KotlinRoot.PATH.toString()
}