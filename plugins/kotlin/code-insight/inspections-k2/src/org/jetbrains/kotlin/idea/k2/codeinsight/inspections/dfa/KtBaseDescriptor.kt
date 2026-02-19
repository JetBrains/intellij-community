// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.value.VariableDescriptor

/**
 * Base variable descriptor for Kotlin variables
 */
interface KtBaseDescriptor: VariableDescriptor {
    /**
     * Returns true if this descriptor describes a variable whose type is an inline class.
     */
    fun isInlineClassReference(): Boolean
}