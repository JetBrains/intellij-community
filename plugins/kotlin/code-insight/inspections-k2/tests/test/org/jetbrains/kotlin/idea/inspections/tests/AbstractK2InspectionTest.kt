// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.tests

import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.inspections.AbstractInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.invalidateCaches
import kotlin.io.path.Path

abstract class AbstractK2InspectionTest : AbstractInspectionTest() {

    override fun inspectionClassDirective() = "// K2_INSPECTION_CLASS:"
    override fun registerGradlePlugin() {}

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun processKotlinScriptIfNeeded(psiFile: PsiFile) {
        if (psiFile !is KtFile || !psiFile.isScript()) return

        // Script configuration is loaded asynchronously. Ensure it is ready before
        // running inspections to avoid a race with `.kts` setup.
        runWithModalProgressBlocking(project, "AbstractK2InspectionTest") {
            KotlinScriptService.getInstance(project).load(psiFile.alwaysVirtualFile)
        }
    }

    override fun doTest(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Path(path), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(path)
        }
    }

    override fun tearDown() {
        runAll(
          { project.invalidateCaches() },
          { super.tearDown() }
        )
    }
}