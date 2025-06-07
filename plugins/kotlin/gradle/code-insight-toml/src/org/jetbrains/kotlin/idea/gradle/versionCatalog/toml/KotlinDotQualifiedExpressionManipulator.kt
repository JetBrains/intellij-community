// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * Allows indexing and then searching for references in KTS files. It is needed to enable findUsages for the elements in KTS
 * files, referring to properties declared in Gradle Version catalog.
 *
 * @see [KotlinGradleTomlVersionCatalogReferencesSearcher]
 * @see [org.jetbrains.plugins.groovy.lang.resolve.GroovyMacroManipulator]
 * @see [com.android.tools.idea.gradle.navigation.KotlinRefManipulator]
 */
private class KotlinDotQualifiedExpressionManipulator : AbstractElementManipulator<KtDotQualifiedExpression>() {
    override fun handleContentChange(element: KtDotQualifiedExpression, range: TextRange, newContent: String?): KtDotQualifiedExpression? {
        return null
    }
}