// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Returns false if the dot-qualified expression could be a full reference to a Gradle version catalog property.
 */
internal fun KtDotQualifiedExpression.hasWrappingVersionCatalogExpression() =
    parent is KtDotQualifiedExpression
        && parent.lastChild is KtNameReferenceExpression // this is false if parent expression ends with `.get()`
