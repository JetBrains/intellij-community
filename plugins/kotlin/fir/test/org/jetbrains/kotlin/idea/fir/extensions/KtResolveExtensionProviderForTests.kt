// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.resolve.extensions.*
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KtResolveExtensionProviderForTests : KtResolveExtensionProvider() {
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
}

private class ExtensionFileForTest(private val rootTag: XmlTag, private val packageName: FqName) : KtResolveExtensionFile() {
    private val functionNames by lazy {
        rootTag.findSubTags("function").mapTo(mutableSetOf()) { Name.identifier(it.getAttributeValue("name")!!) }
    }

    private val classNames by lazy {
        rootTag.findSubTags("class").mapTo(mutableSetOf()) { Name.identifier(it.getAttributeValue("name")!!) }
    }

    override fun buildFileText(): String = buildString {
        appendLine("package $packageName")
        appendLine()

        printClasses(rootTag)
        appendLine()
        printFunctions(rootTag)
    }

    private fun StringBuilder.printFunctions(tag: XmlTag) {
        for (functionDeclaration in tag.findSubTags("function")) {
            val name = functionDeclaration.getAttributeValue("name")
            appendLine("fun $name() {}")
            appendLine()
        }
    }

    private fun StringBuilder.printClasses(tag: XmlTag) {
        for (classDeclaration in tag.findSubTags("class")) {
            val name = classDeclaration.getAttributeValue("name")
            appendLine("class $name {")
            printClasses(classDeclaration)
            printFunctions(classDeclaration)
            appendLine("}")
        }
    }

    override fun createNavigationTargetsProvider(): KtResolveExtensionNavigationTargetsProvider {
        return object : KtResolveExtensionNavigationTargetsProvider() {
            override fun KtAnalysisSession.getNavigationTargets(element: KtElement): Collection<PsiElement> =
                element.parentsWithSelf
                    .filterIsInstance<KtDeclaration>()
                    .firstNotNullOfOrNull { declaration ->
                        val fqNameParts = when (val symbol = declaration.getSymbol()) {
                            is KtFunctionSymbol -> {
                                val callableId = symbol.callableIdIfNonLocal
                                    ?: return@firstNotNullOfOrNull null
                                callableId.className?.pathSegments().orEmpty() + callableId.callableName
                            }

                            is KtClassLikeSymbol -> {
                                symbol.classIdIfNonLocal?.relativeClassName?.pathSegments()
                                    ?: return@firstNotNullOfOrNull null
                            }

                            else -> {
                                return@firstNotNullOfOrNull null
                            }
                        }
                        var tag = rootTag
                        for (part in fqNameParts) {
                            tag = tag.subTags.firstOrNull { it.getAttributeValue("name") == part.asString() }
                                ?: return@firstNotNullOfOrNull null
                        }
                        return@firstNotNullOfOrNull listOf(tag)
                    }
                    ?: listOf(rootTag)
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
        return classNames
    }
}

