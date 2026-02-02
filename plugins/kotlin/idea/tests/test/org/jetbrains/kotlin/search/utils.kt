// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.search

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.KotlinFileType


fun SearchScope.findFiles(vFileRenderer: (VirtualFile) -> String): String {
    if (this is GlobalSearchScope) {
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, this)
            .plus(FileTypeIndex.getFiles(JavaFileType.INSTANCE, this))
            .vFilesToString(vFileRenderer)

        return "global:$files"
    }

    this as LocalSearchScope

    val files = virtualFiles.toList().vFilesToString(vFileRenderer)
    val elements = scope.map { if (it is PsiNamedElement) it.name.toString() else it.toString() }.sortAndJoinToString()

    return "local:\nfiles:$files\nelements:$elements"
}

private fun Iterable<VirtualFile>.vFilesToString(vFileRenderer: (VirtualFile) -> String): String = map(vFileRenderer).sortAndJoinToString()

private fun <T : Comparable<T>> Iterable<T>.sortAndJoinToString(): String = sorted().joinToString(
    prefix = "\n",
    separator = ",\n",
    postfix = ",",
)
