// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.navigation.GotoCheck
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.junit.Assert

abstract class AbstractScriptConfigurationNavigationTest : AbstractScriptConfigurationTest() {

    fun doTest(unused: String) {
        val testDir = testDataFile()
        val text = findMainScript(testDir).readText()

        InTextDirectivesUtils.getPrefixedBoolean(text, "// INDEX_DEPENDENCIES_SOURCES:")?.let {
            Registry.get("kotlin.scripting.index.dependencies.sources").setValue(it)
        }

        configureScriptFile(testDir)
        val reference = file!!.findReferenceAt(myEditor.caretModel.offset)!!

        val resolved = reference.resolve()!!.navigationElement!!

        val expectedReference = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// REF:")
        val actualReference = resolved.renderAsGotoImplementation()

        Assert.assertEquals(expectedReference, actualReference)

        val expectedFile = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// FILE:")
        val actualFile = GotoCheck.getFileWithDir(resolved)

        Assert.assertEquals(expectedFile, actualFile)
    }

    override fun tearDown() {
        super.tearDown()
        Registry.get("kotlin.scripting.index.dependencies.sources").resetToDefault()
    }
}