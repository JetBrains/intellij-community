// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi

import com.google.common.collect.HashMultimap
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

object KotlinPsiHeuristics {
    @JvmStatic
    fun unwrapImportAlias(file: KtFile, aliasName: String): Collection<String> {
        return file.aliasImportMap[aliasName]
    }

    @JvmStatic
    fun unwrapImportAlias(type: KtUserType, aliasName: String): Collection<String> {
        val file = type.containingKotlinFileStub?.psi as? KtFile ?: return emptyList()
        return unwrapImportAlias(file, aliasName)
    }

    @JvmStatic
    fun getImportAliases(file: KtFile, names: Set<String>): Set<String> {
        val result = LinkedHashSet<String>()
        for ((aliasName, name) in file.aliasImportMap.entries()) {
            if (name in names) {
                result += aliasName
            }
        }
        return result
    }

    private val KtFile.aliasImportMap by userDataCached("ALIAS_IMPORT_MAP_KEY") { file ->
        HashMultimap.create<String, String>().apply {
            for (import in file.importList?.imports.orEmpty()) {
                val aliasName = import.aliasName ?: continue
                val name = import.importPath?.fqName?.shortName()?.asString() ?: continue
                put(aliasName, name)
            }
        }
    }

    @JvmStatic
    fun isProbablyNothing(typeReference: KtTypeReference): Boolean {
        val userType = typeReference.typeElement as? KtUserType ?: return false
        return isProbablyNothing(userType)
    }

    @JvmStatic
    fun isProbablyNothing(type: KtUserType): Boolean {
        val referencedName = type.referencedName

        if (referencedName == "Nothing") {
            return true
        }

        // TODO: why don't use PSI-less stub for calculating aliases?
        val file = type.containingKotlinFileStub?.psi as? KtFile ?: return false

        // TODO: support type aliases
        return file.aliasImportMap[referencedName].contains("Nothing")
    }
}