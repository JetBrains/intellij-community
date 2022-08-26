// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.groovy.inspections

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.groovy.KotlinGroovyBundle
import org.jetbrains.kotlin.idea.inspections.PluginVersionDependentInspection
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression

class DifferentKotlinGradleVersionInspection : BaseInspection(), PluginVersionDependentInspection {
    override var testVersionMessage: String? = null
        @TestOnly set

    override fun buildVisitor(): BaseInspectionVisitor = MyVisitor()

    override fun getGroupDisplayName() = getProbableBugs()

    override fun buildErrorString(vararg args: Any): String =
        KotlinGroovyBundle.message("error.text.different.kotlin.gradle.version", args[0], args[1])

    private abstract class VersionFinder : KotlinGradleInspectionVisitor() {
        protected abstract fun onFound(kotlinPluginVersion: IdeKotlinVersion, kotlinPluginStatement: GrCallExpression)

        override fun visitClosure(closure: GrClosableBlock) {
            super.visitClosure(closure)

            val dependenciesCall = closure.getStrictParentOfType<GrMethodCall>() ?: return
            if (dependenciesCall.invokedExpression.text != "dependencies") return

            val buildScriptCall = dependenciesCall.getStrictParentOfType<GrMethodCall>() ?: return
            if (buildScriptCall.invokedExpression.text != "buildscript") return

            val kotlinPluginStatement = GradleHeuristicHelper.findStatementWithPrefix(closure, "classpath").firstOrNull {
                it.text.contains(KOTLIN_PLUGIN_CLASSPATH_MARKER)
            } ?: return

            val kotlinPluginVersion = GradleHeuristicHelper.getHeuristicVersionInBuildScriptDependency(kotlinPluginStatement)
                ?: findResolvedKotlinGradleVersion(closure.containingFile)
                ?: return

            onFound(kotlinPluginVersion, kotlinPluginStatement)
        }
    }

    private inner class MyVisitor : VersionFinder() {
        override fun onFound(kotlinPluginVersion: IdeKotlinVersion, kotlinPluginStatement: GrCallExpression) {
            val latestSupportedLanguageVersion = KotlinPluginLayout.ideCompilerVersion.languageVersion
            val projectLanguageVersion = kotlinPluginVersion.languageVersion

            if (latestSupportedLanguageVersion < projectLanguageVersion || projectLanguageVersion < LanguageVersion.FIRST_SUPPORTED) {
                registerError(kotlinPluginStatement, kotlinPluginVersion, testVersionMessage ?: latestSupportedLanguageVersion)
            }
        }
    }

    companion object {
        fun getKotlinPluginVersion(gradleFile: GroovyFileBase): IdeKotlinVersion? {
            var version: IdeKotlinVersion? = null
            val visitor = object : VersionFinder() {
                override fun visitElement(element: GroovyPsiElement) {
                    element.acceptChildren(this)
                }

                override fun onFound(kotlinPluginVersion: IdeKotlinVersion, kotlinPluginStatement: GrCallExpression) {
                    version = kotlinPluginVersion
                }
            }
            gradleFile.accept(visitor)
            return version
        }
    }
}