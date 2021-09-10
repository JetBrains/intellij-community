/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.refactoring.fqName

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }