// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.types.KotlinType

interface ClassifierNamePolicyEx: ClassifierNamePolicy {

    fun renderClassifierWithType(classifier: ClassifierDescriptor, renderer: DescriptorRenderer, type: KotlinType): String
}