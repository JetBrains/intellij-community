// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractCommonIdePlatformKindTooling

@K1Deprecation
@InternalIgnoreDependencyViolation
class Fe10CommonIdePlatformKindTooling : AbstractCommonIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = Fe10GenericTestIconProvider
}