// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.imports

import org.jetbrains.kotlin.idea.imports.AbstractFilteringAutoImportTestBase

abstract class AbstractK2FilteringAutoImportTest : AbstractFilteringAutoImportTestBase() {
    override fun isFirPlugin(): Boolean = true
}