// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.findUsages

import org.jetbrains.kotlin.idea.test.KotlinJdkAndMultiplatformStdlibDescriptor

abstract class AbstractKotlinFindUsagesWithStdlibTest : AbstractFindUsagesTest() {
    override fun getProjectDescriptor() =
        KotlinJdkAndMultiplatformStdlibDescriptor.JDK_AND_MULTIPLATFORM_STDLIB_WITH_SOURCES
}
