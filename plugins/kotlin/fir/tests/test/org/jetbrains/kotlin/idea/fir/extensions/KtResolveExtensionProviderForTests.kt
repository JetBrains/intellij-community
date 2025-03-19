// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.*
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

@OptIn(KaExperimentalApi::class)
class KtResolveExtensionProviderForTests : KaResolveExtensionProvider() {
    override fun provideExtensionsFor(module: KaModule): List<KaResolveExtension> {
        return when (module) {
            is KaSourceModule -> {
                val ideaModule = module.openapiModule
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

@OptIn(KaExperimentalApi::class)
private class ExtensionForTests(private val xmlFile: XmlFile) : KaResolveExtension() {
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

    override fun getKtFiles(): List<KaResolveExtensionFile> {
        return files
    }
}

@OptIn(KaExperimentalApi::class)
private class ExtensionFileForTest(private val rootTag: XmlTag, private val packageName: FqName) : KaResolveExtensionFile() {
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

    override fun createNavigationTargetsProvider(): KaResolveExtensionNavigationTargetsProvider {
        return object : KaResolveExtensionNavigationTargetsProvider() {
            override fun KaSession.getNavigationTargets(element: KtElement): Collection<PsiElement> =
                element.parentsWithSelf
                    .filterIsInstance<KtDeclaration>()
                    .firstNotNullOfOrNull { declaration ->
                        val fqNameParts = when (val symbol = declaration.symbol) {
                            is KaNamedFunctionSymbol -> {
                                val callableId = symbol.callableId
                                    ?: return@firstNotNullOfOrNull null
                                callableId.className?.pathSegments().orEmpty() + callableId.callableName
                            }

                            is KaClassLikeSymbol -> {
                                symbol.classId?.relativeClassName?.pathSegments()
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

