// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.types

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@K1Deprecation
@Deprecated("This declaration will be removed in the future")
fun KtParameter.typeFqName(): FqName? =
    resolveToParameterDescriptorIfAny(BodyResolveMode.FULL)?.type?.fqName
