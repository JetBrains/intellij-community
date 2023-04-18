// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionFile
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionReferencePsiTargetsProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

abstract class AbstractK2ReferenceResolveWithResolveExtensionTest : AbstractFirReferenceResolveTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("data.xml", """
            <xml>
                <package>generated</package>
                <functions>
                    <function>aaaa</function>
                    <function>bbbb</function>
                    <function>cccc</function>
                </functions>
            </xml>
        """.trimIndent())
        project.extensionArea.getExtensionPoint(KtResolveExtensionProvider.EP_NAME)
            .registerExtension(KtResolveExtensionProviderForTests(), testRootDisposable)
    }
}

private class KtResolveExtensionProviderForTests : KtResolveExtensionProvider() {
    override fun provideExtensionsFor(module: KtModule): List<KtResolveExtension> {
        return when (module) {
            is KtSourceModule -> {
                val ideaModule = module.ideaModule
                val xmlVirtualFile = OrderEnumerator.orderEntries(ideaModule).roots(OrderRootType.SOURCES).roots.firstNotNullOfOrNull {
                    it.findChild("data.xml")
                } ?: return emptyList()
                val xmlPsiFile =
                    PsiManager.getInstance(module.project).findFile(xmlVirtualFile) as? XmlFile ?: return emptyList()
                listOf(ExtensionForTests(xmlPsiFile))
            }

            else -> emptyList()
        }
    }
}

private class ExtensionForTests(private val xmlFile: XmlFile) : KtResolveExtension() {
    private val packageName by lazy {
        xmlFile.rootTag?.findFirstSubTag("package")?.value?.text?.let(::FqName)
    }
    private val files by lazy {
        if (packageName != null) {
            listOf(ExtensionFileForTest(xmlFile.rootTag!!, packageName!!))
        } else emptyList()
    }

    override fun getContainedPackages(): Set<FqName> {
        return setOfNotNull(packageName)
    }

    override fun getKtFiles(): List<KtResolveExtensionFile> {
        return files
    }

    override fun getModificationTracker(): ModificationTracker {
        return ModificationTracker { xmlFile.modificationStamp }
    }
}

private class ExtensionFileForTest(private val rootTag: XmlTag, private val packageName: FqName) : KtResolveExtensionFile() {
    private val functionTags by lazy {
        rootTag.findFirstSubTag("functions")?.findSubTags("function")
    }

    private val functionNames by lazy {
        functionTags?.mapTo(mutableSetOf()) { Name.identifier(it.value.text.trim()) }.orEmpty()
    }

    override fun buildFileText(): String = buildString {
        appendLine("package $packageName")
        appendLine()
        functionNames.forEach { functionName ->
            appendLine("fun $functionName() {}")
            appendLine()
        }
    }

    override fun createPsiTargetsProvider(): KtResolveExtensionReferencePsiTargetsProvider {
        return object : KtResolveExtensionReferencePsiTargetsProvider() {
            override fun KtAnalysisSession.getReferenceTargetsForSymbol(symbol: KtSymbol): Collection<PsiElement> {
                return when (symbol) {
                    is KtFunctionSymbol -> listOfNotNull(
                        functionTags?.firstOrNull { it.value.text == symbol.name.asString() }
                    )

                    else -> listOf(rootTag)
                }
            }
        }
    }

    override fun getFileName(): String {
        return "file.kt"
    }

    override fun getFilePackageName(): FqName {
        return packageName
    }

    override fun getTopLevelCallableNames(): Set<Name> {
        return functionNames
    }

    override fun getTopLevelClassifierNames(): Set<Name> {
        return emptySet()
    }
}

