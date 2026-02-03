// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class KotlinMetadataFileIndex : KotlinMetadataFileIndexBase(ClassId::asSingleFqName) {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinMetadataFileIndex")
    }

    override fun getName() = NAME
}