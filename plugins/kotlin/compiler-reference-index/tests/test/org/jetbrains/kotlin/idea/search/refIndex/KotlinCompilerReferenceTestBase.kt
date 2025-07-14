// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.java.compiler.CompilerReferencesTestBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.currentJavaVersion
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast

abstract class KotlinCompilerReferenceTestBase : CompilerReferencesTestBase(),
                                                 ExpectedPluginModeProvider {

    // it is known that Kotlin 1.9.25 is incompatible with JDK25
    val isCompatibleVersions: Boolean = (pluginMode == KotlinPluginMode.K2 || currentJavaVersion().feature < 25)

    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        if (withK2Compiler) {
            moduleBuilder.addLibrary(KotlinArtifactNames.KOTLIN_STDLIB, TestKotlinArtifacts.kotlinStdlib.path)
        } else {
            // For the K1 tests we want to use a Kotlin 1.x library because Kotlin 2 libraries might not be able to
            // be consumed by the K1 compiler.
            moduleBuilder.addMavenLibrary(JavaModuleFixtureBuilder.MavenLib("org.jetbrains.kotlin:kotlin-stdlib:1.9.25"))
        }
    }

    protected open val withK2Compiler: Boolean
        get() = pluginMode == KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        KotlinCompilerReferenceIndexService[project]
        if (withK2Compiler) {
            project.enableK2Compiler()
        } else {
            project.enableK1Compiler()
        }
    }

    protected fun getReferentFilesForElementUnderCaret(): Set<String>? {
        return getReferentFiles(findDeclarationAtCaret(), true)
    }

    protected open fun findDeclarationAtCaret(): PsiElement {
        val elementAtCaret = myFixture.elementAtCaret
        return elementAtCaret.parentOfType<PsiNamedElement>(withSelf = true) ?: error("declaration at caret not found")
    }

    protected fun getReferentFiles(element: PsiElement, withJavaIndex: Boolean): Set<String>? {
        val fromKotlinIndex = KotlinCompilerReferenceIndexService[project].findReferenceFilesInTests(element)
        val fromJavaIndex = CompilerReferenceService.getInstance(project)
            .takeIf { withJavaIndex }
            ?.cast<CompilerReferenceServiceBase<*>>()
            ?.getReferentFilesForTests(element)

        if (fromKotlinIndex == null && fromJavaIndex == null) return null
        return mutableSetOf<String>().apply {
            fromKotlinIndex?.mapTo(this, VirtualFile::getName)
            fromJavaIndex?.mapTo(this, VirtualFile::getName)
        }
    }

    protected fun findSubOrSuperTypes(name: String, deep: Boolean, subtypes: Boolean): List<String>? {
        val service = KotlinCompilerReferenceIndexService[project]
        val fqName = FqName(name)
        val sequence = if (subtypes)
            service.getSubtypesOfInTests(fqName, deep)
        else
            throw NotImplementedError("supertypes not supported")

        return sequence?.map { it.asString() }?.sorted()?.toList()
    }

    protected fun findHierarchy(hierarchyElement: PsiElement): List<String> = KotlinCompilerReferenceIndexService[project]
        .getSubtypesOfInTests(hierarchyElement)
        .map(FqName::asString)
        .sorted()
        .toList()

    protected fun forEachBoolean(action: (Boolean) -> Unit) = listOf(true, false).forEach(action)
}