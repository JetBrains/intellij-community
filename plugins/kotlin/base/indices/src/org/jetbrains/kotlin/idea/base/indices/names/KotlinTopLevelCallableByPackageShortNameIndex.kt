// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.deserialization.getName

@ApiStatus.Internal
object KotlinTopLevelCallableByPackageShortNameIndex : NameByPackageShortNameIndex() {
    val KEY = ID.create<FqName, List<Name>>(KotlinTopLevelCallableByPackageShortNameIndex::class.java.name)

    override fun getName(): ID<FqName, List<Name>> = KEY

    override fun getDeclarationNamesByKtFile(ktFile: KtFile): List<Name> = buildList {
        for (declaration in ktFile.declarations) {
            if (declaration is KtCallableDeclaration) {
                val name = declaration.nameAsName ?: continue
                if (name.isSpecial) continue
                add(name)
            }
        }
    }

    override fun getDeclarationNamesByMetadata(kotlinJvmBinaryClass: KotlinJvmBinaryClass): List<Name> {
        if (kotlinJvmBinaryClass.classHeader.kind == KotlinClassHeader.Kind.CLASS) return emptyList()
        if (kotlinJvmBinaryClass.classHeader.kind == KotlinClassHeader.Kind.SYNTHETIC_CLASS) return emptyList()
        if (kotlinJvmBinaryClass.classHeader.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS) {
            // MULTIFILE_CLASS does not contain any callables, all callables are inside MULTIFILE_CLASS_PARTs
            return emptyList()
        }

        val (nameResolver, proto) = readProtoPackageData(kotlinJvmBinaryClass) ?: return emptyList()

        return buildList {
            proto.functionList.mapTo(this) { nameResolver.getName(it.name) }
            proto.propertyList.mapTo(this) { nameResolver.getName(it.name) }
        }
    }

    override fun getPackageAndNamesFromBuiltIns(fileContent: FileContent): Map<FqName, List<Name>> {
        // builtins do not contain callables
        return emptyMap()
    }
}