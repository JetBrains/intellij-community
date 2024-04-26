// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight

import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractMultiModuleLineMarkerCodeMetaInfoTest

abstract class AbstractK2MultiModuleLineMarkerTest: AbstractMultiModuleLineMarkerCodeMetaInfoTest() {
    override fun isFirPlugin(): Boolean = true
}