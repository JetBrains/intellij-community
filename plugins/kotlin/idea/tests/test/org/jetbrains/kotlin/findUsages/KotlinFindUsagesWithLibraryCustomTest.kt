// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinFindUsagesWithLibraryCustomTest : AbstractKotlinFindUsagesWithLibraryTest() {
    fun testFindUsagesForLocalClassProperty() {
        val ktParameter = findElementInLibrary<KtParameter>("localClassProperty")
        val usages = findUsages(ktParameter.originalElement, null, false, project)
        assertEquals(2, usages.size)
    }

    fun testFindUsagesForPrivateClass() {
        val privateClass = findElementInLibrary<KtClassOrObject>("PrivateLibraryClass")

        val usages = findUsages(privateClass, null, false, project)
        assertEquals(
            listOf(
                "PrivateLibraryClass (class org.jetbrains.kotlin.references.fe10.KtFe10SimpleNameReference)",
                "library.PrivateLibraryClass (class com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl)",
            ),
            usages.map { it.toString() }.sorted(),
        )
    }

    private inline fun <reified T : PsiElement> findElementInLibrary(text: String): T {
        val libraryFile = FilenameIndex.getFilesByName(project, "library.kt", GlobalSearchScope.everythingScope(project)).first()
        val indexOf = libraryFile.text.indexOf(text)
        return libraryFile.findElementAt(indexOf)!!.getStrictParentOfType()!!
    }
}