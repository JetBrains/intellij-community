// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.checkers

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinCompilerStandalone
import java.io.File

abstract class AbstractJavaAgainstKotlinBinariesCheckerTest : AbstractJavaAgainstKotlinCheckerTest() {
    fun doTest(path: String) {
        val ktFile = File(path)
        val javaFile = File(ktFile.parentFile, ktFile.nameWithoutExtension + ".java")

        val extraOptions = InTextDirectivesUtils.findListWithPrefixes(configFileText ?: "", "// KOTLINC_EXTRA_OPTS")

        val libraryJar = KotlinCompilerStandalone(listOf(ktFile), options = extraOptions).compile()
        val jarUrl = "jar://" + FileUtilRt.toSystemIndependentName(libraryJar.absolutePath) + "!/"
        ModuleRootModificationUtil.addModuleLibrary(module, jarUrl)

        val ktFileText = FileUtil.loadFile(ktFile, true)
        val allowAstForCompiledFile = InTextDirectivesUtils.isDirectiveDefined(ktFileText, AstAccessControl.ALLOW_AST_ACCESS_DIRECTIVE)

        if (allowAstForCompiledFile) {
            allowTreeAccessForAllFiles()
        }

        doTest(true, true, javaFile.toRelativeString(File(testDataPath)))
    }
}
