// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

internal fun testLightClass(
    testData: File,
    suffixes: List<String> = listOf("descriptors"),
    normalize: (String) -> String,
    findLightClass: (String) -> PsiClass?,
    membersFilter: PsiClassRenderer.MembersFilter = PsiClassRenderer.MembersFilter.DEFAULT,
) {
    val expected = UltraLightChecker.getJavaFileForTest(testData, suffixes)
    val actual = LightClassTestCommon.getActualLightClassText(
        testData,
        findLightClass = findLightClass,
        normalizeText = { text ->
            //NOTE: ide and compiler differ in names generated for parameters with unspecified names
            text.replace("java.lang.String s,", "java.lang.String p,")
                .replace("java.lang.String s)", "java.lang.String p)")
                .replace("java.lang.String s1", "java.lang.String p1")
                .replace("java.lang.String s2", "java.lang.String p2")
                .replace("java.lang.Object o)", "java.lang.Object p)")
                .replace("java.lang.String[] strings", "java.lang.String[] p")
                .removeLinesStartingWith("@" + JvmAnnotationNames.METADATA_FQ_NAME.asString())
                .run(normalize)
        },
        membersFilter = membersFilter,
    )

    KotlinTestUtils.assertEqualsToFile(expected, actual)
}

fun findLightClass(fqName: String, ktFile: KtFile?, project: Project): PsiClass? {
    ktFile?.script?.let {
        return it.toLightClass()
    }

    return JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.allScope(project))
        ?: PsiTreeUtil.findChildrenOfType(ktFile, KtClassOrObject::class.java)
            .find { fqName.endsWith(it.nameAsName!!.asString()) }
            ?.toLightClass()
}

private fun String.removeLinesStartingWith(prefix: String): String {
    return lines().filterNot { it.trimStart().startsWith(prefix) }.joinToString(separator = "\n")
}
