// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.findUsages

import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinFindUsagesWithLibraryCustomTest : AbstractKotlinFindUsagesWithLibraryTest() {
    fun testFindUsagesForLocalClassProperty() {
        val libraryFile = FilenameIndex.getFilesByName(project, "library.kt", GlobalSearchScope.everythingScope(project)).first()
        val indexOf = libraryFile.text.indexOf("localClassProperty")
        val ktParameter = libraryFile.findElementAt(indexOf)!!.getStrictParentOfType<KtParameter>()!!
        val usages = findUsages(ktParameter.originalElement, null, false, project)
        assertEquals(2, usages.size)
    }
}