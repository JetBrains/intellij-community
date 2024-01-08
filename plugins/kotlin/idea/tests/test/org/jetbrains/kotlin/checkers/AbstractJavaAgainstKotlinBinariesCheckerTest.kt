// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.checkers

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.FileTreeAccessFilter
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.decompiled.light.classes.origin.KotlinDeclarationInCompiledFileSearcher
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File

abstract class AbstractJavaAgainstKotlinBinariesCheckerTest : AbstractJavaAgainstKotlinCheckerTest() {
    fun doTest(path: String) {
        val ktFile = File(path)
        val javaFile = File(ktFile.parentFile, ktFile.nameWithoutExtension + ".java")

        val compilerArguments = InTextDirectivesUtils.findListWithPrefixes(
            configFileText ?: "", CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE
        )

        val libraryJar = KotlinCompilerStandalone(listOf(ktFile), options = compilerArguments).compile()
        val jarUrl = "jar://" + FileUtilRt.toSystemIndependentName(libraryJar.absolutePath) + "!/"
        ModuleRootModificationUtil.addModuleLibrary(module, jarUrl)

        val ktFileText = FileUtil.loadFile(ktFile, true)
        val allowAstForCompiledFile = InTextDirectivesUtils.isDirectiveDefined(ktFileText, AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE)

        if (allowAstForCompiledFile) {
            allowTreeAccessForAllFiles()
        }

        assertTreeAccess(jarUrl, allowAstForCompiledFile, path, ktFileText)
        doTest(true, true, javaFile.toRelativeString(File(testDataPath)))
    }

    private fun assertTreeAccess(libraryUrl: String, allowAstForCompiledFile: Boolean, ktFilePath: String, ktFileText: String) {
        val classFiles = mutableListOf<KtClsFile>()
        FileTypeIndex.processFiles(
            JavaClassFileType.INSTANCE,
            Processor {
                if (it.url.startsWith(libraryUrl)) {
                    it.toPsiFile(project)?.safeAs<KtClsFile>()?.let(classFiles::add)
                }
                true
            },
            ProjectScope.getLibrariesScope(project),
        )

        assertNotEmpty(classFiles)
        val lightClasses = classFiles.flatMap { file ->
            file.declarations
                .filterIsInstance<KtClassOrObject>()
                .map { it.toLightClass()!! }
                .plus(listOfNotNull(file.findFacadeClass()))
        }

        classFiles.forEach { assertFalse(it.isContentsLoaded) }

        val unwrappedMap = mutableMapOf<PsiMember, PsiElement?>()
        fun unwrapWithAssert(element: PsiMember) {
            val result = kotlin.runCatching {
                unwrappedMap[element] = element.unwrapped
            }

            if (!allowAstForCompiledFile) {
                result.exceptionOrNull()?.let {
                    fail("access to tree from ${element.name}")
                }
            } else {
                result.getOrThrow()
            }
        }

        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(FileTreeAccessFilter(), testRootDisposable)
        var wasException = false
        try {
            fun check(lightClass: KtLightClass) {
                unwrapWithAssert(lightClass)

                for (method in lightClass.methods) {
                    unwrapWithAssert(method)
                }

                for (field in lightClass.fields) {
                    unwrapWithAssert(field)
                }

                for (innerClass in lightClass.innerClasses) {
                    check(innerClass as KtLightClass)
                }
            }

            for (lightClass in lightClasses) {
                check(lightClass)
            }
        } catch (e: Throwable) {
            if (allowAstForCompiledFile && e.message?.startsWith("Access to tree elements not allowed for") == true) {
                wasException = true
            } else {
                throw e
            }
        } finally {
            PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, testRootDisposable)
        }

        if (allowAstForCompiledFile != wasException) {
            throw FileComparisonFailedError(
                /* message = */ "Redundant '// ${AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE}'",
                /* expected = */ ktFileText,
                /* actual = */
                ktFileText.lines().filterNot {
                    it.startsWith("// ${AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE}")
                }.joinToString("\n"),
                /* expectedFilePath = */ ktFilePath,
            )
        }

        if (allowAstForCompiledFile) return

        classFiles.forEach {
            it.children
            assertTrue(it.isContentsLoaded)
        }

        for ((member, unwrapped) in unwrappedMap.entries) {
            if (member is PsiClass) continue

            val file = member.containingFile.cast<PsiCompiledElement>().mirror as KtClsFile
            val unwrappedByAst = KotlinDeclarationInCompiledFileSearcher.getInstance().findDeclarationInCompiledFile(
                file,
                member,
            )

            if (unwrapped != null) {
                assertEquals("${member.name}", unwrapped, unwrappedByAst)
            }
        }

        classFiles.forEach {
            runWriteAction { it.onContentReload() }
            assertFalse(it.isContentsLoaded)
        }
    }
}
