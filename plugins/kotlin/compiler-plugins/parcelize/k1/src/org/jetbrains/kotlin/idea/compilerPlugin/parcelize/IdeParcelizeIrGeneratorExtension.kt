// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize

import org.jetbrains.kotlin.parcelize.ParcelizeIrGeneratorExtension
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.parcelize.AdditionalAnnotations

internal class IdeParcelizeIrGeneratorExtension : ParcelizeIrGeneratorExtension(
    AdditionalAnnotations(
        parcelize = ParcelizeNames.PARCELIZE_CLASS_FQ_NAMES,
        ignoredOnParcel = ParcelizeNames.IGNORED_ON_PARCEL_FQ_NAMES,
    )
)