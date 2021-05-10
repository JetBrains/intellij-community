// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.editor.quickDoc.AbstractQuickDocProviderTest.wrapToFileComparisonFailure
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext.DECLARATION_TO_DESCRIPTOR
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class KDocLinkMultiModuleResolveTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("kdoc/multiModuleResolve")

    fun testSimple() {
        val code = module("code")
        val samples = module("samples", hasTestRoot = true)

        samples.addDependency(code)

        doInfoTest("code/usage.kt")
    }

    fun testFqName() {
        val code = module("code")
        val samples = module("samples", hasTestRoot = true)

        samples.addDependency(code)

        doInfoTest("code/usage.kt")
        doResolveSampleTest("samples")
        doResolveSampleTest("samples.SampleGroup")
        doResolveSampleTest("samples.megasamples")
        doResolveSampleTest("samples.megasamples.MegaSamplesGroup")
        doResolveSampleTest("samples.notindir")
        doResolveSampleTest("samples.notindir.NotInDirSamples")
        doResolveSampleTest("samplez")
        doResolveSampleTest("samplez.a")
        doResolveSampleTest("samplez.a.b")
        doResolveSampleTest("samplez.a.b.c")
        doResolveSampleTest("samplez.a.b.c.Samplez")
    }

    fun testTypeParameters() {
        val code = module("code")
        val samples = module("samples", hasTestRoot = true)

        samples.addDependency(code)

        doInfoTest("code/usageSingleTypeParameter.kt")
        doInfoTest("code/usageNestedTypeParameters.kt")
    }

    private fun doResolveSampleTest(link: String) {
        configureByFile("${getTestName(true)}/code/usage.kt")
        val documentationManager = DocumentationManager.getInstance(myProject)
        val targetElement = documentationManager.findTargetElement(myEditor, file)

        targetElement as KtElement

        val bindingContext = targetElement.analyze()
        val descriptor = bindingContext[DECLARATION_TO_DESCRIPTOR, targetElement]!!
        val kdoc = descriptor.findKDoc()!! as KDocSection
        val resolutionFacade = targetElement.getResolutionFacade()
        assertNotEmpty(resolveKDocLink(bindingContext, resolutionFacade, descriptor, kdoc.findTagByName("sample")!!, link.split(".")))
    }

    private fun doInfoTest(path: String) {
        val fullPath = "${getTestName(true)}/$path"
        val testDataFile = File(testDataPath, fullPath)
        configureByFile(fullPath)
        val documentationManager = DocumentationManager.getInstance(myProject)
        val targetElement = documentationManager.findTargetElement(myEditor, file)
        val originalElement = DocumentationManager.getOriginalElement(targetElement)

        var info = DocumentationManager.getProviderFromElement(targetElement).generateDoc(targetElement, originalElement)
        if (info != null) {
            info = StringUtil.convertLineSeparators(info)
        }
        if (info != null && !info.endsWith("\n")) {
            info += "\n"
        }

        val textData = FileUtil.loadFile(testDataFile, true)
        val directives = InTextDirectivesUtils.findLinesWithPrefixesRemoved(textData, false, true, "INFO:")

        if (directives.isEmpty()) {
            throw FileComparisonFailure(
                "'// INFO:' directive was expected",
                textData,
                textData + "\n\n//INFO: " + info,
                testDataFile.absolutePath
            )
        } else {
            val expectedInfo = directives.joinToString("\n", postfix = "\n")

            if (expectedInfo.endsWith("...\n")) {
                if (!info!!.startsWith(expectedInfo.removeSuffix("...\n"))) {
                    wrapToFileComparisonFailure(info, testDataFile.absolutePath, textData)
                }
            } else if (expectedInfo != info) {
                wrapToFileComparisonFailure(info!!, testDataFile.absolutePath, textData)
            }
        }
    }

    fun testSeeTagFqName() {
        module("usage")
        module("code")

        doResolveTest("usage/usage.kt", KtClass::class.java)
    }

    fun testMarkdownLinkFqName() {
        module("usage")
        module("code")

        doResolveTest("usage/usage.kt", KtNamedFunction::class.java)
    }

    fun testSamePackages() {
        module("usage")
        module("code")

        doResolveTest("usage/foo/bar/usage.kt", KtClass::class.java)
    }

    private fun doResolveTest(path: String, clazz: Class<*>) {
        configureByFile("${getTestName(true)}/$path")
        val element = file.findReferenceAt(editor.caretModel.offset)
        val resolvedElement = element?.resolve()
        assertNotNull(resolvedElement)
        assertInstanceOf(resolvedElement, clazz)
    }

}