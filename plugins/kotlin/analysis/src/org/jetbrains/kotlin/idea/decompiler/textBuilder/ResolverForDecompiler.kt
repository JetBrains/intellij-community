// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.textBuilder

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.ClassId

interface ResolverForDecompiler {
    fun resolveTopLevelClass(classId: ClassId): ClassDescriptor?

    fun resolveDeclarationsInFacade(facadeFqName: FqName): List<DeclarationDescriptor>
}
