// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.projectStructure.scopes

import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopeUtil
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.getOrderedRoots

/**
 * Converts a [GlobalSearchScope] to a string representation for testing purposes.
 *
 * Handles special cases like union scopes, intersection scopes, and [CombinableSourceAndClassRootsScope].
 */
fun GlobalSearchScope.renderAsTestOutput(): String {
    val builder = IndentedStringBuilder()
    builder.renderScope(this)
    return builder.toString()
}

private fun IndentedStringBuilder.renderScope(scope: GlobalSearchScope) {
    val unionComponents = GlobalSearchScopeUtil.flattenUnionScope(scope)
    when {
        unionComponents.size > 1 -> {
            appendLine("UnionScope")
            indent {
                unionComponents
                    .sortedBy { it::class.simpleName }
                    .forEach(::renderScope)
            }
        }

        GlobalSearchScopeUtil.isIntersectionScope(scope) -> {
            appendLine("IntersectionScope")
            indent {
                val components = GlobalSearchScopeUtil.flattenIntersectionScope(scope)
                components
                    .sortedBy { it::class.simpleName }
                    .forEach(::renderScope)
            }
        }

        scope is GlobalSearchScope.FilesScope -> {
            renderClassName(scope)
            indent {
                appendLine("files:")
                indent {
                    scope.files.map { it.name }.sorted().forEach { fileName ->
                        appendLine(fileName)
                    }
                }
            }
        }

        scope is CombinableSourceAndClassRootsScope -> {
            renderClassName(scope)
            indent {
                appendLine("roots:")
                indent {
                    scope.getOrderedRoots().forEach { root ->
                        appendLine(root.name)
                    }
                }
            }
        }

        // This case covers `NotScope`.
        scope is DelegatingGlobalSearchScope -> {
            renderClassName(scope)
            indent {
                renderScope(scope.delegate)
            }
        }

        else -> {
            renderClassName(scope)
        }
    }
}

private fun IndentedStringBuilder.renderClassName(searchScope: GlobalSearchScope) {
    appendLine(searchScope::class.simpleName ?: searchScope.displayName)
}

private class IndentedStringBuilder {
    private val builder = StringBuilder()
    private var currentIndent = ""

    fun indent(action: IndentedStringBuilder.() -> Unit) {
        val previousIndent = currentIndent
        currentIndent += "    "
        action()
        currentIndent = previousIndent
    }

    fun appendLine(content: String) {
        builder.append(currentIndent)
            .append(content)
            .append("\n")
    }

    override fun toString(): String = builder.toString()
}
