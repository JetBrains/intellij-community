// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.MockLibraryFacility

abstract class AbstractNavigateFromLibrarySourcesTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun navigationElementForReferenceInLibrarySource(filePath: String, referenceText: String): PsiElement {
        val libraryOrderEntry = ModuleRootManager.getInstance(module).orderEntries
            .first { it is LibraryOrderEntry && it.libraryName == MockLibraryFacility.MOCK_LIBRARY_NAME }
            as LibraryOrderEntry

        val libSourcesRoot = libraryOrderEntry.getRootUrls(OrderRootType.SOURCES)[0]
        val libUrl = "$libSourcesRoot/$filePath"
        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(libUrl) ?: error("Can't find library: $libUrl")
        val psiFile = psiManager.findFile(virtualFile) ?: error("Can't find PSI file for $virtualFile")
        val indexOf = psiFile.text.indexOf(referenceText)
        val reference = psiFile.findReferenceAt(indexOf) ?: error("Can't find reference at index $indexOf")
        val resolvedElement = reference.resolve() ?: error("Can't resolve reference")
        return resolvedElement.navigationElement ?: error("Can't get navigation element")
    }
}
