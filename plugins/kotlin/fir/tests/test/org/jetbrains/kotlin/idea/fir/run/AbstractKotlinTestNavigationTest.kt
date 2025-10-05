// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.run

import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

abstract class AbstractKotlinTestNavigationTest : KotlinLightCodeInsightFixtureTestCase() {

    fun doTest(filePath: String) {
        val testDataFile = dataFile()

        val mainFileName = testDataFile.name
        val mainFileBaseName = FileUtil.getNameWithoutExtension(mainFileName)
        val extraFiles = testDataFile.parentFile.listFiles { _, name ->
            name != mainFileName && name.startsWith("$mainFileBaseName.") && (name.endsWith(".kt") || name.endsWith(".java"))
        } ?: emptyArray()

        (extraFiles + testDataFile).associateBy { myFixture.configureByFile(it.name) }
        val protocol = InTextDirectivesUtils.findStringWithPrefixes(myFixture.file.text, "// PROTOCOL: ")!!
        val path = InTextDirectivesUtils.findStringWithPrefixes(myFixture.file.text, "// PATH: ")!!
        val result = InTextDirectivesUtils.findStringWithPrefixes(myFixture.file.text, "// RESULT: ")!!

        val location = JavaTestLocator.INSTANCE.getLocation(
            protocol,
            path,
            project,
            GlobalSearchScope.projectScope(project)
        )
        assertNotEmpty(location)
        val element = location.first().psiElement.unwrapped as? KtNamedDeclaration
        assertNotNull(element)
        assertEquals(element!!.name, result)
    }
}