// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.expectActual

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.name.FqName

class KotlinTypeInaccessibleException(fqNames: Collection<FqName?>) : Exception() {
    override val message: String = KotlinBundle.message(
        "type.0.1.is.not.accessible.from.target.module",
        fqNames.size,
        TypeAccessibilityChecker.typesToString(fqNames)
    )

    private fun TypeAccessibilityChecker.Companion.typesToString(types: Collection<FqName?>, separator: CharSequence = "\n"): String {
        return types.toSet().joinToString(separator = separator) {
            it?.shortName()?.asString() ?: "<Unknown>"
        }
    }
}