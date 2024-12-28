// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.editor.quickDoc.AbstractQuickDocProviderTest
import java.io.File

abstract class AbstractFirQuickDocTest : AbstractQuickDocProviderTest() {

    override fun getDirectives(textData: String?): List<String?> {
        val directives = InTextDirectivesUtils.findLinesWithPrefixesRemoved(textData, false, true, "K2_INFO:")
        if (directives.isNotEmpty()) return directives
        return super.getDirectives(textData)
    }

    override fun getDoc(): String? {
        val target =
            IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, file, editor.caretModel.offset).firstOrNull()
                ?: return null
        return computeDocumentationBlocking(target.createPointer())?.html
    }

    override fun doTest(path: String) {
        val miscDirectory = dataFile().parentFile.parentFile
        if (miscDirectory.name.equals("misc")) {
            File(miscDirectory, "dependencies").listFiles().forEach {
                myFixture.addFileToProject(it.name, it.readText())
            }
        }
        super.doTest(path)
    }
}