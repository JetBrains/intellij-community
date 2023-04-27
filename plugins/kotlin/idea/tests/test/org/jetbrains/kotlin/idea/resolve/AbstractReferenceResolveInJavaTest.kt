// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtDeclaration
import org.junit.Assert

private val DIRECTORY_WITH_KOTLIN_CODE = IDEA_TEST_DATA_DIR.resolve("resolve/referenceInJava/dependency")

abstract class AbstractReferenceResolveInJavaTest : AbstractReferenceResolveTest() {
    override fun doTest(path: String) {
        val fileName = fileName()
        assert(fileName.endsWith(".java")) { fileName }
        DIRECTORY_WITH_KOTLIN_CODE.listFiles()?.forEach {
            myFixture.configureByText(it.name, FileUtil.loadFile(it, true))
        }

        myFixture.configureByFile(fileName)
        performChecks()
    }
}

abstract class AbstractReferenceToCompiledKotlinResolveInJavaTest : AbstractReferenceResolveTest() {
    private val mockLibraryFacility = MockLibraryFacility(DIRECTORY_WITH_KOTLIN_CODE)

    override fun doTest(path: String) {
        myFixture.configureByFile(fileName())
        performChecks()
    }

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun getExpectedReferences(text: String, index: Int): List<String> {
        return getExpectedReferences(text, index, "CLS_REF")
    }

    override fun checkResolvedTo(element: PsiElement) {
        val navigationElement = element.navigationElement
        Assert.assertFalse(
            "Reference should not navigate to a light element\nWas: ${navigationElement::class.java.simpleName}",
            navigationElement is KtLightElement<*, *>
        )
        Assert.assertTrue(
            "Reference should navigate to a kotlin declaration\nWas: ${navigationElement::class.java.simpleName}",
            navigationElement is KtDeclaration || navigationElement is KtClsFile
        )
    }
}
