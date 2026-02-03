// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleBuildFileHighlightingTest
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1GradleBuildFileHighlightingTest : AbstractGradleBuildFileHighlightingTest() {
    override fun checkHighlighting(file: VirtualFile) {
      runInEdtAndWait {
        runReadAction {
          val psiFile = PsiManager.getInstance(myProject).findFile(file) as? KtFile
                        ?: error("Couldn't find psiFile for virtual file: ${file.canonicalPath}")

          ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)

          val ktsFileUnderTest = File(testDataDirectory(), file.relativeToProjectRoot())
          val ktsFileHighlighting = ktsFileUnderTest.resolveSibling("${ktsFileUnderTest.path}$outputFileExt")
          val directives = parseDirectives(ktsFileUnderTest.readText())

          checkHighlighting(
            psiFile,
            ktsFileHighlighting,
            directives,
            myProject,
            highlightWarnings = true
          )
        }
      }
    }
}