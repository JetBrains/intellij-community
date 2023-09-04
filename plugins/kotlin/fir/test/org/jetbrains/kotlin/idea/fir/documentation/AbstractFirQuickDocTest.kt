// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.editor.quickDoc.AbstractQuickDocProviderTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.utils.IgnoreTests
import kotlin.io.path.Path

abstract class AbstractFirQuickDocTest : AbstractQuickDocProviderTest() {
    override fun getDoc(): @Nls String? {
        val target =
            IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, file, editor.caretModel.offset).firstOrNull()
                ?: return null
        return computeDocumentationBlocking(target.createPointer())?.html
    }

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun doTest(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Path(path), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(path)
        }
    }
}