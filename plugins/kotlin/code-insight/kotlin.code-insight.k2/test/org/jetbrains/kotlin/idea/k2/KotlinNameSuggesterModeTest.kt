// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Case
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class KotlinNameSuggesterModeTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    fun testCamel() = test("Foo.Bar.Baz", Case.CAMEL, "baz", "barBaz", "fooBarBaz")
    fun testPascal() = test("Foo.Bar.Baz", Case.PASCAL, "Baz", "BarBaz", "FooBarBaz")
    fun testSnake() = test("Foo.Bar.Baz", Case.SNAKE, "baz", "bar_baz", "foo_bar_baz")
    fun testScreamingSnake() = test("Foo.Bar.Baz", Case.SCREAMING_SNAKE, "BAZ", "BAR_BAZ", "FOO_BAR_BAZ")
    fun testKebab() = test("Foo.Bar.Baz", Case.KEBAB, "baz", "bar-baz", "foo-bar-baz")

    fun testRootPackage() = test(SpecialNames.ROOT_PACKAGE, Case.PASCAL, "Value")
    fun testNoNameProvided() = test(SpecialNames.NO_NAME_PROVIDED, Case.CAMEL, "value")
    fun testAnonymous() = test(SpecialNames.ANONYMOUS, Case.SCREAMING_SNAKE, "ANONYMOUS")
    fun testSpecialInitCamel() = test(SpecialNames.INIT, Case.CAMEL, "init")
    fun testSpecialInitPascal() = test(SpecialNames.INIT, Case.PASCAL, "Init")

    fun testKeywordInit() = test("init", Case.CAMEL, "init")
    fun testKeywordClassCamel() = test("class", Case.CAMEL, "klass", "clazz")
    fun testKeywordClassPascal() = test("class", Case.PASCAL, "Class")
    fun testKeywordPackageCamel() = test("package", Case.CAMEL, "pkg")
    fun testKeywordPackagePascal() = test("package", Case.PASCAL, "Package")
    fun testKeywordWhenCamel() = test("when", Case.CAMEL, "`when`")
    fun testKeywordWhenPascal() = test("when", Case.PASCAL, "When")

    private fun test(name: Name, case: Case, vararg names: String) {
        test(ClassId.topLevel(FqName.topLevel(name)), case, *names)
    }

    private fun test(classIdString: String, case: Case, vararg names: String) {
        test(ClassId.fromString(classIdString), case, *names)
    }

    private fun test(classId: ClassId, case: Case, vararg names: String) {
        val actualNames = KotlinNameSuggester(case).suggestClassNames(classId).toList().sorted()
        TestCase.assertEquals(names.sorted(), actualNames)
    }

    override fun getTestDataPath() = KotlinRoot.PATH.toString()
}