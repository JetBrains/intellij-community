// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import java.io.File
import java.util.regex.Pattern

object LightClassTestCommon {
    internal val SKIP_IDE_TEST_DIRECTIVE = "SKIP_IDE_TEST"

    private val SUBJECT_FQ_NAME_PATTERN = Pattern.compile("^//\\s*(.*)$", Pattern.MULTILINE)
    private const val NOT_GENERATED_DIRECTIVE = "// NOT_GENERATED"

    fun getActualLightClassText(
        testDataFile: File,
        findLightClass: (String) -> PsiClass?,
        normalizeText: (String) -> String,
        membersFilter: PsiClassRenderer.MembersFilter = PsiClassRenderer.MembersFilter.DEFAULT
    ): String {
        val text = FileUtil.loadFile(testDataFile, true)
        val matcher = SUBJECT_FQ_NAME_PATTERN.matcher(text)
        TestCase.assertTrue("No FqName specified. First line of the form '// f.q.Name' expected", matcher.find())
        val fqName = matcher.group(1)

        val lightClass = findLightClass(fqName)

        return actualText(fqName, lightClass, normalizeText, membersFilter)
    }

    private fun actualText(
        fqName: String?,
        lightClass: PsiClass?,
        normalizeText: (String) -> String,
        membersFilter: PsiClassRenderer.MembersFilter
    ): String {
        if (lightClass == null) {
            return NOT_GENERATED_DIRECTIVE
        }
        TestCase.assertTrue("Not a light class: $lightClass ($fqName)", lightClass is KtLightClass)
        return normalizeText(PsiClassRenderer.renderClass(lightClass, renderInner = true, membersFilter = membersFilter))
    }

    // Actual text for light class is generated with ClsElementImpl.appendMirrorText() that can find empty DefaultImpl inner class in stubs
    // for all interfaces. This inner class can't be used in Java as it generally is not seen from light classes built from Kotlin sources.
    // It is also omitted during classes generation in backend so it also absent in light classes built from compiled code.
    fun removeEmptyDefaultImpls(text: String): String = text.replace("\n    static final class DefaultImpls {\n    }\n", "")
}