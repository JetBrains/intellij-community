// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.utils.fqname

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType


val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }