// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.vfilefinder.KotlinJvmModuleAnnotationsIndex
import org.jetbrains.kotlin.idea.vfilefinder.KotlinModuleMappingIndex
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.metadata.jvm.deserialization.PackageParts
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.serialization.deserialization.ClassData
import org.jetbrains.kotlin.serialization.deserialization.MetadataPartProvider

class IDEPackagePartProvider(val scope: GlobalSearchScope) : PackagePartProvider, MetadataPartProvider {
    override fun findPackageParts(packageFqName: String): List<String> =
        getPackageParts(packageFqName).flatMap(PackageParts::parts).distinct()

    override fun findMetadataPackageParts(packageFqName: String): List<String> =
        getPackageParts(packageFqName).flatMap(PackageParts::metadataParts).distinct()

    private fun getPackageParts(packageFqName: String): MutableList<PackageParts> =
        FileBasedIndex.getInstance().getValues(KotlinModuleMappingIndex.NAME, packageFqName, scope)

    // Note that in case of several modules with the same name, we return all annotations on all of them, which is probably incorrect
    override fun getAnnotationsOnBinaryModule(moduleName: String): List<ClassId> =
        FileBasedIndex.getInstance().getValues(KotlinJvmModuleAnnotationsIndex.NAME, moduleName, scope).flatten()

    // Optional annotations are not needed in IDE because they can only be used in common module sources, and they are loaded via the
    // standard common module resolution there. (In the CLI compiler the situation is different because we compile common+platform
    // sources together, _without_ common dependencies.)
    override fun getAllOptionalAnnotationClasses(): List<ClassData> =
        emptyList()

    // NB: It's ok even to return a little more than actual packages for non-class entities
    override fun computePackageSetWithNonClassDeclarations(): Set<String> = buildSet {
        FileBasedIndex.getInstance().processAllKeys(KotlinModuleMappingIndex.NAME, { name -> add(name); true }, scope, null)
    }
}
