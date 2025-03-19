// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testIntegration

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinTestFinder
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinTestFinder : AbstractKotlinTestFinder() {
    override fun isResolvable(classOrObject: KtClassOrObject): Boolean =
        classOrObject.resolveToDescriptorIfAny() != null

}
