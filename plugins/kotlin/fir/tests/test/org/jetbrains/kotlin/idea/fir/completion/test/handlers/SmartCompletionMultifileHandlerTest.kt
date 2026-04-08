// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.completion.test.COMPLETION_TEST_DATA_BASE
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class SmartCompletionMultifileHandlerTest : KotlinFixtureCompletionBaseTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testImportExtensionFunction() {
        doTest()
    }

    fun testImportExtensionProperty() {
        doTest()
    }

    //KTIJ-38120
    fun _testAnonymousObjectGenericJava() {
        doTest()
    }

    fun testImportAnonymousObject() {
        doTest()
    }

    // KTIJ-38121
    fun _testNestedSamAdapter() {
        doTest(lookupString = "Nested")
    }

    private fun doTest(lookupString: String? = null, itemText: String? = null) {
        val fileName = getTestName(false)

        val fileNames = listOf("$fileName-1.kt", "$fileName-2.kt", "$fileName.java")

        myFixture.configureByFiles(*fileNames.filter { File(testDataDirectory, it).exists() }.toTypedArray())

        val items = complete(CompletionType.SMART, 1)
        if (items != null) {
            fun isMatching(lookupElement: LookupElement): Boolean {
                if (lookupString != null && lookupElement.lookupString != lookupString) return false

                val presentation = LookupElementPresentation()
                lookupElement.renderElement(presentation)
                if (itemText != null && presentation.itemText != itemText) return false

                return true
            }

            val matchedItems = items.filter(::isMatching)
            when (matchedItems.size) {
                0 -> fail("No matching items found")
                1 -> selectItem(items[0], Lookup.NORMAL_SELECT_CHAR)
                else -> fail("Multiple matching items found")
            }
        }

        myFixture.checkResultByFile("$fileName.kt.after")
    }

    override val testDataDirectory: File
        get() = COMPLETION_TEST_DATA_BASE.resolve("handlers/multifile/smart")

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
    override fun getPlatform(): TargetPlatform = JvmPlatforms.unspecifiedJvmPlatform
    override fun getProjectDescriptor() = JAVA_LATEST
}
