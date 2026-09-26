// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.groovy

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.toGroovyRepositorySnippet
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SettingsScriptBuilder
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

class GroovySettingsScriptBuilder(scriptFile: GroovyFile): SettingsScriptBuilder<GroovyFile>(scriptFile) {
    override fun addPluginRepository(repository: RepositoryDescription) {
        addPluginRepositoryExpression(repository.toGroovyRepositorySnippet())
    }

    override fun buildPsiFile(project: Project): GroovyFile {
        return GroovyPsiElementFactory
            .getInstance(project)
            .createGroovyFile(build(), false, null)
    }
}