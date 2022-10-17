// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.overrideImplement

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class BodyType(val requiresReturn: Boolean = true) {
    object NoBody : BodyType()
    object EmptyOrTemplate : BodyType(requiresReturn = false)
    object FromTemplate : BodyType(requiresReturn = false)
    object Super : BodyType()
    object QualifiedSuper : BodyType()

    class Delegate(val receiverName: String) : BodyType()

    fun effectiveBodyType(canBeEmpty: Boolean): BodyType {
        return if (!canBeEmpty && this == EmptyOrTemplate) FromTemplate else this
    }
}

