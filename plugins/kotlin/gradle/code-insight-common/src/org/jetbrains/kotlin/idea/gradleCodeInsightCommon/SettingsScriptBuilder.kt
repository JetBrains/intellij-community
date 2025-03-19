// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription

abstract class SettingsScriptBuilder<T: PsiFile>(private val scriptFile: T) {
    private val builder = StringBuilder(scriptFile.text)

    private fun findBlockBody(blockName: String, startFrom: Int = 0): Int {
        val blockOffset = builder.indexOf(blockName, startFrom)
        if (blockOffset < 0) return -1
        return builder.indexOf('{', blockOffset + 1) + 1
    }

    private fun getOrPrependTopLevelBlockBody(blockName: String): Int {
        val blockBody = findBlockBody(blockName)
        if (blockBody >= 0) return blockBody
        builder.insert(0, "$blockName {}\n")
        return findBlockBody(blockName)
    }

    private fun getOrAppendInnerBlockBody(blockName: String, offset: Int): Int {
        val repositoriesBody = findBlockBody(blockName, offset)
        if (repositoriesBody >= 0) return repositoriesBody
        builder.insert(offset, "\n$blockName {}\n")
        return findBlockBody(blockName, offset)
    }

    private fun appendExpressionToBlockIfAbsent(expression: String, offset: Int) {
        var braceCount = 1
        var blockEnd = offset
        for (i in offset..builder.lastIndex) {
            when (builder[i]) {
                '{' -> braceCount++
                '}' -> braceCount--
            }
            if (braceCount == 0) {
                blockEnd = i
                break
            }
        }
        if (!builder.substring(offset, blockEnd).contains(expression.trim())) {
            builder.insert(blockEnd, "\n$expression\n")
        }
    }

    private fun getOrCreatePluginManagementBody() = getOrPrependTopLevelBlockBody("pluginManagement")

    protected fun addPluginRepositoryExpression(expression: String) {
        val repositoriesBody = getOrAppendInnerBlockBody("repositories", getOrCreatePluginManagementBody())
        appendExpressionToBlockIfAbsent(expression, repositoriesBody)
    }

    fun addMavenCentralPluginRepository() {
        addPluginRepositoryExpression("mavenCentral()")
    }

    abstract fun addPluginRepository(repository: RepositoryDescription)

    fun addIncludedModules(modules: List<String>) {
        builder.append(modules.joinToString(prefix = "include ", postfix = "\n") { "'$it'" })
    }

    fun build() = builder.toString()

    abstract fun buildPsiFile(project: Project): T
}
