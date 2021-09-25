// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.util

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.stubs.StubElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

fun KtUserType.aliasImportMap(): Multimap<String, String> {
    // we need to access containing file via stub because getPsi() may return null when indexing and getContainingFile() will crash
    val file = stub?.getContainingFileStub()?.psi ?: return HashMultimap.create()
    return (file as KtFile).aliasImportMap()
}

fun KtFile.aliasImportMap(): Multimap<String, String> {
    val cached = getUserData(ALIAS_IMPORT_DATA_KEY)
    val modificationStamp = modificationStamp
    if (cached != null && modificationStamp == cached.fileModificationStamp) {
        return cached.map
    }

    val data = CachedAliasImportData(buildAliasImportMap(), modificationStamp)
    putUserData(ALIAS_IMPORT_DATA_KEY, data)
    return data.map
}

private fun KtFile.buildAliasImportMap(): Multimap<String, String> {
    val map = HashMultimap.create<String, String>()
    val importList = importList ?: return map
    for (import in importList.imports) {
        val aliasName = import.aliasName ?: continue
        val name = import.importPath?.fqName?.shortName()?.asString() ?: continue
        map.put(aliasName, name)
    }
    return map
}

private class CachedAliasImportData(val map: Multimap<String, String>, val fileModificationStamp: Long)

private val ALIAS_IMPORT_DATA_KEY = Key<CachedAliasImportData>("ALIAS_IMPORT_MAP_KEY")

fun KtTypeReference?.isProbablyNothing(): Boolean {
    val userType = this?.typeElement as? KtUserType ?: return false
    return userType.isProbablyNothing()
}

//TODO: support type aliases
fun KtUserType?.isProbablyNothing(): Boolean {
    if (this == null) return false
    val referencedName = referencedName
    return referencedName == "Nothing" || aliasImportMap()[referencedName].contains("Nothing")
}

private fun StubElement<*>.getContainingFileStub(): PsiFileStub<*> {
    return if (this is PsiFileStub)
        this
    else
        parentStub.getContainingFileStub()
}
