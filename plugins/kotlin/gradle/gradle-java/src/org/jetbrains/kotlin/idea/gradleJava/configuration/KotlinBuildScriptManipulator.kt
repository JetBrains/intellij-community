// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.buildArgumentString
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.getFeatureMentionInCompilerArgsRegex
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.DEFAULT_GRADLE_PLUGIN_REPOSITORY
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.configuration.MAVEN_CENTRAL
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.configuration.getRepositoryForVersion
import org.jetbrains.kotlin.idea.configuration.isRepositoryConfigured
import org.jetbrains.kotlin.idea.configuration.toGradleCompileScope
import org.jetbrains.kotlin.idea.configuration.toKotlinRepositorySnippet
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.COMPILER_OPTIONS
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.DefinedKotlinPluginManagementVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.FOOJAY_RESOLVER_CONVENTION_NAME
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.FOOJAY_RESOLVER_NAME
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptManipulator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport.Companion.IMPLEMENTATION
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport.Companion.TEST_IMPLEMENTATION
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport.Companion.TEST_LIB_ID
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleVersionProvider
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.assertApplicableInMultiplatform
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.canBeConfigured
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.fetchGradleVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.scope
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.useNewSyntax
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.usesNewMultiplatform
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.CompilerOption
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getCompilerOption
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.kotlinVersionIsEqualOrHigher
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.utils.addToStdlib.cast

class KotlinBuildScriptManipulator(
    override val scriptFile: KtFile,
    override val preferNewSyntax: Boolean
) : GradleBuildScriptManipulator<KtFile> {
    override fun isApplicable(file: PsiFile): Boolean = file is KtFile

    private val gradleVersion = GradleVersionProvider.fetchGradleVersion(scriptFile)

    override fun usesOldSyntax(kotlinPluginName: String): Boolean = runReadAction {
        scriptFile.containsApplyKotlinPlugin(kotlinPluginName)
    }

    override fun isConfiguredWithOldSyntax(kotlinPluginName: String): Boolean =
        usesOldSyntax(kotlinPluginName) && runReadAction {
            !hasKotlinPluginApplyFalse()
        }

    override fun isConfigured(kotlinPluginExpression: String): Boolean = runReadAction {
        scriptFile.containsKotlinPluginInPluginsGroup(kotlinPluginExpression) && !hasKotlinPluginApplyFalse()
    }

    override fun configureProjectBuildScript(kotlinPluginName: String, version: IdeKotlinVersion): Boolean {
        if (useNewSyntax(kotlinPluginName, gradleVersion)) return false

        val originalText = scriptFile.text
        scriptFile.getBuildScriptBlock()?.apply {
            addDeclarationIfMissing("var $GSK_KOTLIN_VERSION_PROPERTY_NAME: String by extra", true).also {
                addExpressionAfterIfMissing("$GSK_KOTLIN_VERSION_PROPERTY_NAME = \"$version\"", it)
            }

            getRepositoriesBlock()?.apply {
                addRepositoryIfMissing(version)
                addMavenCentralIfMissing()
            }

            getDependenciesBlock()?.addPluginToClassPathIfMissing()
        }

        return originalText != scriptFile.text
    }

    override fun configureSettingsFile(kotlinPluginName: String, version: IdeKotlinVersion): Boolean {
        val originalText = scriptFile.text
        scriptFile.getOrCreatePluginManagementBlock()?.findOrCreateBlock("plugins")?.let {
            if (it.findPluginInPluginsGroup(kotlinPluginName) == null) {
                it.addExpressionIfMissing(
                    "$kotlinPluginName version \"${version.artifactVersion}\""
                ) as? KtCallExpression
            }
        }
        return originalText != scriptFile.text
    }

    override fun getKotlinVersion(): IdeKotlinVersion? {
        val module = scriptFile.module ?: return null
        return KotlinCompilerVersionProvider.getVersion(module)
    }

    private fun getPluginsBlock(): KtBlockExpression? {
        return scriptFile.findScriptInitializer("plugins")?.getBlock()
    }

    override fun hasExplicitlyDefinedKotlinVersion(): Boolean {
        val pluginsBlock = getPluginsBlock() ?: return false
        return pluginsBlock.findPluginExpressions(::isKotlinPluginIdentifier)?.versionExpression != null
    }

    override fun hasKotlinPluginApplyFalse(): Boolean {
        val pluginsBlock = getPluginsBlock() ?: return false
        val kotlinPluginExpression = pluginsBlock.findPluginExpressions(::isKotlinPluginIdentifier)
        return kotlinPluginExpression?.applyExpression?.arguments?.firstOrNull()?.isFalseConstant() == true
    }

    override fun findAndRemoveKotlinVersionFromBuildScript(): Boolean {
        val pluginsBlock = getPluginsBlock() ?: return false
        val pluginExpression = pluginsBlock.findPluginExpressions(::isKotlinPluginIdentifier)
        pluginExpression?.versionExpression?.let {
            it.delete()
            return true
        }
        return false
    }

    override fun PsiElement.findParentBlock(name: String): PsiElement? {
        val parent = PsiTreeUtil.findFirstParent(this) { elem ->
            (elem is KtCallExpression && elem.calleeExpression?.referencedNameOrNull()?.contains(name) == true) ||
                    (elem is KtDotQualifiedExpression && elem.containsNameReference(name))
        }
        when (parent) {
            is KtCallExpression -> {
                return parent.getBlock()
            }

            is KtDotQualifiedExpression -> {
                return parent
            }

            else -> {
                return null
            }
        }
    }

    override fun PsiElement.getAllVariableStatements(variableName: String): List<PsiElement> {
        val assignments = PsiTreeUtil.findChildrenOfType(this, KtBinaryExpression::class.java)
            .filter { it.left?.containsNameReference(variableName) == true && it.operationReference.getReferencedName() == "=" }

        val setterName = "set${variableName.capitalize()}"
        val setterCalls = PsiTreeUtil.findChildrenOfType(this, KtCallExpression::class.java)
            .filter { it.calleeExpression?.referencedNameOrNull() == setterName && it.valueArguments.size == 1 }

        val propertyCalls = PsiTreeUtil.findChildrenOfType(this, KtDotQualifiedExpression::class.java)
            .mapNotNull { stmt ->
                if (!stmt.receiverExpression.containsNameReference(variableName)) return@mapNotNull null
                val callExpression = stmt.selectorExpression as? KtCallExpression ?: return@mapNotNull null
                val calleeName = callExpression.calleeExpression?.referencedNameOrNull()
                if (calleeName?.contains("set") == true && callExpression.valueArguments.size == 1) {
                    stmt
                } else {
                    null
                }
            }

        return (assignments + setterCalls + propertyCalls).sortedBy { it.startOffset }
    }

    override fun configureBuildScripts(
        kotlinPluginName: String,
        kotlinPluginExpression: String,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?,
        changedFiles: ChangedConfiguratorFiles
    ) {
        changedFiles.storeOriginalFileContent(scriptFile)
        val useNewSyntax = useNewSyntax(kotlinPluginName, gradleVersion)
        scriptFile.apply {
            if (useNewSyntax) {
                createPluginInPluginsGroupIfMissing(kotlinPluginExpression, addVersion, version)
                getRepositoriesBlock()?.apply {
                    val repository = getRepositoryForVersion(version)
                    if (repository != null) {
                        scriptFile.module?.getBuildScriptSettingsPsiFile()?.let {
                            changedFiles.storeOriginalFileContent(it)
                            with(GradleBuildScriptSupport.getManipulator(it)) {
                                addPluginRepository(repository)
                                addMavenCentralPluginRepository()
                                addPluginRepository(DEFAULT_GRADLE_PLUGIN_REPOSITORY)
                            }
                        }
                    }
                }
            } else {
                script?.blockExpression?.addDeclarationIfMissing("val $GSK_KOTLIN_VERSION_PROPERTY_NAME: String by extra", true)
                getApplyBlock()?.createPluginIfMissing(kotlinPluginName)
            }
            getDependenciesBlock()?.addKotlinTestDependencyIfMissing()
            getRepositoriesBlock()?.apply {
                addRepositoryIfMissing(version)
                addMavenCentralIfMissing()
            }

            configureToolchainOrKotlinCompilerOptions(jvmTarget, version, gradleVersion, changedFiles)
        }
    }

    override fun configurePluginInPluginsGroup(
        kotlinPluginExpression: String,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        applyFalse: Boolean,
        changedFiles: ChangedConfiguratorFiles
    ) {
        changedFiles.storeOriginalFileContent(scriptFile)
        scriptFile.createPluginInPluginsGroupIfMissing(kotlinPluginExpression, addVersion, version, applyFalse)
    }

    override fun configurePluginOptions(kotlinPluginName: String, changedFiles: ChangedConfiguratorFiles, vararg options: String) {
        scriptFile.apply {
            findOrCreateScriptInitializer(kotlinPluginName, false)?.let { block ->
                for (option in options) {
                    block.addExpressionIfMissing(option)
                }
            }
        }
    }

    override fun changeLanguageFeatureConfiguration(
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ): PsiElement? = scriptFile.changeLanguageFeatureConfiguration(feature, state, forTests)

    private fun changeKotlinLanguageParameter(parameterName: String, value: String, forTests: Boolean): PsiElement? {
        return if (usesNewMultiplatform()) {
            // For multiplatform projects, we configure the language level for all sourceSets
            // Note: It does not allow only targeting test sourceSets
            val kotlinBlock = scriptFile.getKotlinBlock() ?: return null
            val sourceSetsBlock = kotlinBlock.findOrCreateBlock("sourceSets") ?: return null
            val allBlock = sourceSetsBlock.findOrCreateBlock("all") ?: return null
            val languageSettingsBlock = allBlock.findOrCreateBlock("languageSettings") ?: return null
            languageSettingsBlock.addParameterAssignment(parameterName, value) {
                replace(psiFactory.createExpression("$parameterName = \"$value\""))
            }
        } else {
            scriptFile.changeKotlinTaskParameter(parameterName, value, forTests)
        }
    }

    override fun changeLanguageVersion(version: String, forTests: Boolean): PsiElement? =
        changeKotlinLanguageParameter("languageVersion", version, forTests)

    override fun changeApiVersion(version: String, forTests: Boolean): PsiElement? =
        changeKotlinLanguageParameter("apiVersion", version, forTests)

    override fun addKotlinLibraryToModuleBuildScript(
        targetModule: Module?,
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    ) {
        val codeStyleManager = CodeStyleManager.getInstance(scriptFile.project)

        if (targetModule != null && targetModule.isMultiPlatformModule) {
            val blockExpression = addKotlinMultiplatformDependencyWithConventionSourceSets(
                scriptFile,
                targetModule, scope,
                libraryDescriptor.libraryGroupId, libraryDescriptor.libraryArtifactId,
                libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
            )
            if (blockExpression != null) {
                val elementToReformat = blockExpression.parentOfType<KtBlockExpression>() ?: blockExpression
                codeStyleManager.reformat(elementToReformat, true)
                return
            }
        }

        val dependencyText = getCompileDependencySnippet(
            libraryDescriptor.libraryGroupId,
            libraryDescriptor.libraryArtifactId,
            libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
            scope.toGradleCompileScope(targetModule)
        )

        val dependenciesBlock = if (targetModule != null && usesNewMultiplatform()) {
            val findOrCreateTargetSourceSet = scriptFile
                .getKotlinBlock()
                ?.getSourceSetsBlock()
                ?.findOrCreateTargetSourceSet(targetModule.name.takeLastWhile { it != '.' })
            findOrCreateTargetSourceSet?.getDependenciesBlock()
        } else {
            scriptFile.getDependenciesBlock()
        }

        dependenciesBlock?.let {
            it.addExpressionIfMissing(dependencyText)
            codeStyleManager.reformat(it.parent, true)
        }
    }

    override fun addKotlinLibraryToModuleBuildScriptModCommand(
        targetModule: Module?,
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    ): ModCommand {
        val actionContext = ActionContext(scriptFile.project, scriptFile, 0, TextRange(0, scriptFile.textLength), null)
        return ModCommand.psiUpdate(actionContext) {
            val file = it.getWritable(scriptFile)
            if (targetModule != null && targetModule.isMultiPlatformModule) {
                addKotlinMultiplatformDependencyWithConventionSourceSets(
                    file,
                    targetModule,
                    scope,
                    libraryDescriptor.libraryGroupId, libraryDescriptor.libraryArtifactId,
                    libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
                )?.let { return@psiUpdate }
            }

            val dependencyText = getCompileDependencySnippet(
                libraryDescriptor.libraryGroupId,
                libraryDescriptor.libraryArtifactId,
                libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
                scope.toGradleCompileScope(targetModule)
            )

            val dependenciesBlock = if (targetModule != null && usesNewMultiplatform()) {
                val findOrCreateTargetSourceSet = file
                    .getKotlinBlock()
                    ?.getSourceSetsBlock()
                    ?.findOrCreateTargetSourceSet(targetModule.name.takeLastWhile { it != '.' })
                findOrCreateTargetSourceSet?.getDependenciesBlock()
            } else {
                file.getDependenciesBlock()
            }

            dependenciesBlock?.addExpressionIfMissing(dependencyText)
        }
    }

    private fun KtBlockExpression.findOrCreateTargetSourceSet(moduleName: String) =
        findTargetSourceSet(moduleName) ?: createTargetSourceSet(moduleName)

    private fun KtBlockExpression.findTargetSourceSet(moduleName: String): KtBlockExpression? = statements.find {
        it.isTargetSourceSetDeclaration(moduleName)
    }?.getOrCreateBlock()

    private fun KtExpression.getOrCreateBlock(): KtBlockExpression? = when (this) {
        is KtCallExpression -> getBlock() ?: addBlock()
        is KtReferenceExpression -> replace(KtPsiFactory(project).createExpression("$text {\n}")).cast<KtCallExpression>().getBlock()
        is KtProperty -> delegateExpressionOrInitializer?.getOrCreateBlock()
        else -> error("Impossible create block for $this")
    }

    private fun KtCallExpression.addBlock(): KtBlockExpression? = parent.addAfter(
        KtPsiFactory(project).createEmptyBody(), this
    ) as? KtBlockExpression

    private fun KtBlockExpression.createTargetSourceSet(moduleName: String) = addExpressionIfMissing("getByName(\"$moduleName\") {\n}")
        .cast<KtCallExpression>()
        .getBlock()

    override fun addFoojayPlugin(changedFiles: ChangedConfiguratorFiles, foojayVersion: String) {
        val settingsFile = scriptFile.module?.let {
            it.getTopLevelBuildScriptSettingsPsiFile() as? KtFile
        } ?: return
        changedFiles.storeOriginalFileContent(settingsFile)

        if (!settingsFile.canBeConfigured()) {
            return
        }
        addFoojayPlugin(settingsFile, foojayVersion)
    }

    override fun addFoojayPlugin(settingsFile: PsiFile, foojayVersion: String) {
        if (settingsFile !is KtFile) return
        val pluginBlock = settingsFile.getSettingsPluginsBlock() ?: return
        if (pluginBlock.findPluginInPluginsGroup("id(\"$FOOJAY_RESOLVER_NAME\")") != null) return
        if (pluginBlock.findPluginInPluginsGroup("id(\"$FOOJAY_RESOLVER_CONVENTION_NAME\")") != null) return
        pluginBlock.addExpressionIfMissing("id(\"$FOOJAY_RESOLVER_CONVENTION_NAME\") version \"$foojayVersion\"")
    }

    private fun addPluginRepositoryExpression(expression: String) {
        scriptFile.getOrCreatePluginManagementBlock()?.findOrCreateBlock("repositories")?.addExpressionIfMissing(expression)
    }

    override fun addMavenCentralPluginRepository() {
        addPluginRepositoryExpression("mavenCentral()")
    }

    override fun addPluginRepository(repository: RepositoryDescription) {
        addPluginRepositoryExpression(repository.toKotlinRepositorySnippet())
    }

    override fun addResolutionStrategy(pluginId: String) {
        scriptFile
            .getOrCreatePluginManagementBlock()
            ?.findOrCreateBlock("resolutionStrategy")
            ?.findOrCreateBlock("eachPlugin")
            ?.addExpressionIfMissing(
                """
                        if (requested.id.id == "$pluginId") {
                            useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}{requested.version}")
                        }
                    """.trimIndent()
            )
    }

    override fun addKotlinToolchain(targetVersionNumber: String) {
        scriptFile.getKotlinBlock()?.addExpressionIfMissing("jvmToolchain($targetVersionNumber)")
    }

    override fun addKotlinExtendedDslToolchain(targetVersionNumber: String) {
        scriptFile.getKotlinBlock()?.findOrCreateBlock("jvmToolchain")
            ?.addExpressionIfMissing("(this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of($targetVersionNumber))")
    }

    override fun changeKotlinTaskParameter(
        parameterName: String,
        parameterValue: String,
        forTests: Boolean,
        kotlinVersion: IdeKotlinVersion
    ): PsiElement? {
        return scriptFile.changeKotlinTaskParameter(parameterName, parameterValue, forTests, kotlinVersion)
    }

    private fun KtBlockExpression.addKotlinTestDependencyIfMissing(): KtCallExpression? =
        addExpressionIfMissing(
            text = getCompileDependencySnippet(
                groupId = KOTLIN_GROUP_ID,
                artifactId = TEST_LIB_ID,
                version = GSK_KOTLIN_VERSION_PROPERTY_NAME,
                compileScope = gradleVersion.scope(TEST_IMPLEMENTATION)
            )
        ) as? KtCallExpression

    private fun KtFile.containsApplyKotlinPlugin(pluginName: String): Boolean =
        findScriptInitializer("apply")?.getBlock()?.findPlugin(pluginName) != null

    private fun KtFile.containsKotlinPluginInPluginsGroup(pluginName: String): Boolean =
        findScriptInitializer("plugins")?.getBlock()?.findPluginInPluginsGroup(pluginName) != null

    private fun KtBlockExpression.findPlugin(pluginName: String): KtCallExpression? {
        if (pluginName.isBlank()) return null
        return PsiTreeUtil.getChildrenOfType(this, KtCallExpression::class.java)?.find {
            val calleeName = it.calleeExpression?.referencedNameOrNull()
            (calleeName == "plugin" || calleeName == "id") &&
                    it.valueArguments.firstOrNull()?.getArgumentExpression()?.extractStringValue() == pluginName
        }
    }

    /**
     * Extracts the value of a string literal expression via PSI.
     *
     * Unwraps surrounding parentheses, then for a [KtStringTemplateExpression] returns the text of its
     * single entry (only plain string literals without interpolation are supported). Returns `null` for
     * any other expression shape.
     */
    private fun KtExpression.extractStringValue(): String? {
        val unwrapped = KtPsiUtil.deparenthesize(this)
        val stringTemplate = unwrapped as? KtStringTemplateExpression ?: return null
        return stringTemplate.entries.singleOrNull()?.text
    }

    private fun KtBlockExpression.findPluginInPluginsGroup(pluginName: String): PluginExpression? {
        if (pluginName.isBlank()) return null
        return findPluginExpressions { methodName, arguments ->
            val firstArgument = arguments.singleOrNull() ?: return@findPluginExpressions false
            val firstArgumentValue = firstArgument.extractStringValue() ?: return@findPluginExpressions false
            "${methodName}(\"${firstArgumentValue}\")" == pluginName
        }
    }

    private class PluginExpression(
        val entireExpression: KtExpression,
        val versionExpression: ChainedMethodCallPart?,
        val applyExpression: ChainedMethodCallPart?
    )

    internal class ChainedMethodCallPart(
        val methodName: String,
        val arguments: List<KtExpression>,
        /**
         * Important!: You may only delete one such part per method call, a second deletion will have no effect.
         * To delete more than one part, you will have to parse the method call chain again and call delete on the new chain!
         */
        val delete: () -> Unit
    )

    internal fun KtExpression.parsePluginCallChain(): List<ChainedMethodCallPart>? {
        return when (this) {
            is KtBinaryExpression -> {
                val methodName = operationReference.getReferencedName()
                val leftCallChain = left?.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, listOf(right ?: return null)) {
                    left?.let {
                        this.replace(it)
                    }
                }
            }

            is KtDotQualifiedExpression -> {
                val selectorExpression = selectorExpression as? KtCallExpression ?: return null
                val methodName = selectorExpression.calleeExpression?.referencedNameOrNull() ?: return null
                val arguments = selectorExpression.valueArguments.mapNotNull { it.getArgumentExpression() }
                val leftCallChain = receiverExpression.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, arguments) {
                    this.replace(receiverExpression)
                }
            }

            is KtCallExpression -> {
                val methodName = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
                val arguments = valueArguments.mapNotNull { it.getArgumentExpression() }
                listOf(ChainedMethodCallPart(methodName, arguments) {
                    this.delete() // delete entire expression
                })
            }

            else -> null
        }
    }

    private fun KtBlockExpression.findPluginExpressions(pluginSelector: (String, List<KtExpression>) -> Boolean): PluginExpression? {
        return this.childrenOfType<KtExpression>().firstNotNullOfOrNull { entireExpression ->
            val callParts = entireExpression.parsePluginCallChain() ?: return@firstNotNullOfOrNull null
            val first = callParts.firstOrNull() ?: return@firstNotNullOfOrNull null
            if (!pluginSelector(first.methodName, first.arguments)) return@firstNotNullOfOrNull null

            val versionExpression = callParts.firstOrNull { it.methodName == "version" }
            val applyExpression = callParts.firstOrNull { it.methodName == "apply" }
            PluginExpression(entireExpression, versionExpression, applyExpression)
        }
    }

    private fun isKotlinPluginIdentifier(methodName: String, arguments: List<KtExpression>): Boolean {
        val firstArgumentText = arguments.singleOrNull()?.extractStringValue() ?: return false
        if (methodName == "id") {
            return firstArgumentText == "org.jetbrains.kotlin.jvm"
        } else if (methodName == "kotlin") {
            return firstArgumentText == "jvm"
        }
        return false
    }

    override fun findKotlinPluginManagementVersion(): DefinedKotlinPluginManagementVersion? {
        val versionExpression = scriptFile.getPluginManagementBlock()
            ?.findBlock("plugins")
            ?.findPluginExpressions(::isKotlinPluginIdentifier)?.versionExpression?.arguments?.singleOrNull() ?: return null
        return DefinedKotlinPluginManagementVersion(
            parsedVersion = versionExpression.extractStringValue()?.let { IdeKotlinVersion.opt(it) }
        )
    }

    private fun KtFile.findScriptInitializer(startsWith: String): KtScriptInitializer? =
        PsiTreeUtil.findChildrenOfType(this, KtScriptInitializer::class.java).find { it.text.startsWith(startsWith) }

    private fun KtBlockExpression.findBlock(name: String): KtBlockExpression? {
        return getChildrenOfType<KtCallExpression>().find {
            it.calleeExpression?.referencedNameOrNull() == name &&
                    it.valueArguments.singleOrNull()?.getArgumentExpression() is KtLambdaExpression
        }?.getBlock()
    }

    internal fun KtScriptInitializer.getBlock(): KtBlockExpression? =
        PsiTreeUtil.findChildOfType(this, KtCallExpression::class.java)?.getBlock()

    internal fun KtCallExpression.getBlock(): KtBlockExpression? =
        (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
            ?: lambdaArguments.lastOrNull()?.getLambdaExpression()?.bodyExpression

    private fun KtFile.getOrCreatePluginManagementBlock(): KtBlockExpression? = findOrCreateScriptInitializer("pluginManagement", true)

    private fun KtFile.getPluginManagementBlock(): KtBlockExpression? = findScriptInitializer("pluginManagement")?.getBlock()

    internal fun KtFile.getKotlinBlock(): KtBlockExpression? = findOrCreateScriptInitializer("kotlin")

    private fun KtBlockExpression.getSourceSetsBlock(): KtBlockExpression? = findOrCreateBlock("sourceSets")

    private fun KtFile.getRepositoriesBlock(): KtBlockExpression? = findOrCreateScriptInitializer("repositories")

    private fun KtFile.getDependenciesBlock(): KtBlockExpression? = findOrCreateScriptInitializer("dependencies")

    private fun KtFile.getPluginsBlock(): KtBlockExpression? = findOrCreateScriptInitializer("plugins", true)

    private fun KtFile.getSettingsPluginsBlock(): KtBlockExpression? {
        val pluginsInitializer = findScriptInitializer("plugins")?.getBlock()
        if (pluginsInitializer != null) {
            return pluginsInitializer
        } else {
            val pluginManagementScriptInitializer = findScriptInitializer("pluginManagement")
            return if (pluginManagementScriptInitializer != null) {
                val pluginsScriptInitializer = psiFactory.createScriptInitializer("plugins {\n}")
                val addedElement =
                    script?.blockExpression?.addAfter(pluginsScriptInitializer, pluginManagementScriptInitializer) as? KtScriptInitializer
                addedElement?.addNewLinesIfNeeded()
                addedElement?.getBlock()
            } else findOrCreateScriptInitializer("plugins", true)
        }
    }

    private fun KtFile.createPluginInPluginsGroupIfMissing(
        pluginName: String,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        applyFalse: Boolean = false
    ) {
        getPluginsBlock()?.let {
            val existingPluginDefinition = it.findPluginInPluginsGroup(pluginName)
            if (existingPluginDefinition?.applyExpression != null) {
                // Cannot properly handle `apply`, `delete` and `redo`
                existingPluginDefinition.entireExpression.delete()
            }
            if (existingPluginDefinition?.applyExpression != null || existingPluginDefinition?.versionExpression == null) {
                it.addExpressionIfMissing(pluginExpression(pluginName, addVersion, version, applyFalse))
            }
        }
    }

    private fun pluginExpression(
        pluginName: String,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        applyFalse: Boolean
    ): String = buildString {
        append(pluginName)
        if (addVersion) {
            append(" version \"")
            append(version.artifactVersion)
            append('"')
        }
        if (applyFalse) {
            append(" apply false")
        }
    }

    private fun KtFile.createApplyBlock(): KtBlockExpression? {
        val apply = psiFactory.createScriptInitializer("apply {\n}")
        val plugins = findScriptInitializer("plugins")
        val addedElement = plugins?.addSibling(apply) ?: addToScriptBlock(apply)
        addedElement?.addNewLinesIfNeeded()
        return (addedElement as? KtScriptInitializer)?.getBlock()
    }

    private fun KtFile.getApplyBlock(): KtBlockExpression? = findScriptInitializer("apply")?.getBlock() ?: createApplyBlock()

    private fun KtBlockExpression.createPluginIfMissing(pluginName: String): KtCallExpression? =
        findPlugin(pluginName) ?: addExpressionIfMissing("plugin(\"$pluginName\")") as? KtCallExpression

    private fun KtFile.changeLanguageFeatureConfiguration(
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ): PsiElement? {
        if (usesNewMultiplatform()) {
            state.assertApplicableInMultiplatform()
            return getKotlinBlock()
                ?.findOrCreateBlock("sourceSets")
                ?.findOrCreateBlock("all")
                ?.addExpressionIfMissing("languageSettings.enableLanguageFeature(\"${feature.name}\")")
        }

        val kotlinVersion = getKotlinVersion()
        val featureArgumentString = feature.buildArgumentString(state, kotlinVersion)
        val parameterName = "freeCompilerArgs"
        val parameterNameAndValueExpression = psiFactory.createExpression("$parameterName = listOf(\"$featureArgumentString\")")
        return addOrReplaceKotlinTaskParameter(
            parameterName,
            parameterNameAndValueExpression,
            forTests
        ) { _, _ ->
            replaceLanguageFeature(feature, state, kotlinVersion)
            // If we had a `.add(...)` call with a single argument, replace it with `.addAll(...)` for multiple arguments
            replaceCallee(from = "add", to = "addAll")
            this
        }
    }

    /**
     * PSI-based replacement for [org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.replaceLanguageFeature].
     *
     * Updates [this] in place so that the [feature] flag corresponding to [state] is present (and unique)
     * among string-literal arguments of the relevant argument list. If the feature is already mentioned,
     * the matching portion of the string literal is replaced with the new argument string. Otherwise,
     * a new string-literal argument is appended to the argument list.
     *
     * Supported shapes of [this]:
     *  - `freeCompilerArgs = listOf("...", ...)` ([KtBinaryExpression])
     *  - `freeCompilerArgs.add("...")` / `freeCompilerArgs.addAll("...", ...)` ([KtDotQualifiedExpression])
     *  - `freeCompilerArgs.addAll(listOf("...", ...))` / `freeCompilerArgs.set(listOf(...))`
     */
    private fun KtExpression.replaceLanguageFeature(
        feature: LanguageFeature,
        state: LanguageFeature.State,
        kotlinVersion: IdeKotlinVersion?,
    ) {
        val argumentList = findArgumentListForLanguageFeatures() ?: return
        val featureArgumentString = feature.buildArgumentString(state, kotlinVersion)
        val regex = feature.getFeatureMentionInCompilerArgsRegex()

        for (argument in argumentList.arguments) {
            val stringTemplate = argument.getArgumentExpression() as? KtStringTemplateExpression ?: continue
            // We only handle plain string literals (no interpolation) like "-XXLanguage:+Foo".
            val literalText = stringTemplate.entries.singleOrNull()?.text ?: continue
            val match = regex.find(literalText) ?: continue
            if (match.value != featureArgumentString) {
                val newLiteralText = literalText.replace(match.value, featureArgumentString)
                stringTemplate.replace(psiFactory.createStringTemplate(newLiteralText))
            }
            return
        }

        // The feature is not mentioned yet — append a new string-literal argument.
        argumentList.addArgument(psiFactory.createArgument("\"$featureArgumentString\""))
    }

    /**
     * Locates the [KtValueArgumentList] that holds the string-literal arguments containing the language
     * feature flags for the supported shapes documented on [replaceLanguageFeature].
     */
    private fun KtExpression.findArgumentListForLanguageFeatures(): KtValueArgumentList? {
        val call: KtCallExpression = when (this) {
            // e.g. `freeCompilerArgs = listOf(...)`
            is KtBinaryExpression -> right as? KtCallExpression ?: return null
            // e.g. `freeCompilerArgs.add(...)` / `.addAll(...)` / `.set(listOf(...))`
            is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression ?: return null
            else -> return null
        }

        // Unwrap `.addAll(listOf(...))` / `.set(listOf(...))` to the inner `listOf`'s argument list.
        val singleArgCall = call.valueArguments.singleOrNull()?.getArgumentExpression() as? KtCallExpression
        return singleArgCall?.valueArgumentList ?: call.valueArgumentList
    }

    private fun KtExpression.replaceCallee(from: String, to: String) {
        val calleeExpression = this.getPossiblyQualifiedCallExpression()?.calleeExpression
        if (calleeExpression?.referencedNameOrNull() == from) {
            calleeExpression.replace(psiFactory.createExpression(to))
        }
    }

    private fun KtBlockExpression.addParameterAssignment(
        parameterName: String,
        parameterValue: String,
        replaceIt: KtExpression.() -> PsiElement
    ): PsiElement {
        return statements.filterIsInstance<KtBinaryExpression>().firstOrNull { stmt ->
            stmt.left?.matchesNameReference(parameterName) == true
        }?.replaceIt() ?: addExpressionIfMissing("$parameterName = \"$parameterValue\"")
    }

    private fun projectSupportsCompilerOptions(file: PsiFile, kotlinVersion: IdeKotlinVersion?): Boolean {
        /*
        // The current test infrastructure uses either a fallback version of Kotlin –
        org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings.Companion.getFallbackVersionForOutdatedCompiler
        or a currently bundled version. Not that one that is stated in build scripts
         */
        return kotlinVersionIsEqualOrHigher(major = 1, minor = 8, patch = 0, file, kotlinVersion)
    }

    /**
     * Currently, this function is called to add or replace parameters:
     * freeCompilerArgs
     * languageVersion
     * apiVersion
     * jvmTarget
     *
     * @param parameterNameAndValueExpression the full PSI expression to insert when the parameter is missing
     * (e.g. `freeCompilerArgs = listOf(...)` or `freeCompilerArgs.addAll(...)`). The function does not
     * build the assignment itself because a task parameter may be added some other way.
     */
    private fun KtFile.addOrReplaceKotlinTaskParameter(
        parameterName: String,
        parameterNameAndValueExpression: KtExpression,
        forTests: Boolean,
        kotlinVersion: IdeKotlinVersion? = null,
        replaceIt: KtExpression.(/* precomputedReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement? {
        val taskName = if (forTests) "compileTestKotlin" else "compileKotlin"
        val kotlinOptionsBlock = findScriptInitializer("$taskName.kotlinOptions")?.getBlock()
        // We leave deprecated `kotlinOptions` untouched, it can be updated with `kotlinOptions` to `compilerOptions` inspection
        return if (kotlinOptionsBlock != null) {
            val assignment = kotlinOptionsBlock.statements.find {
                (it as? KtBinaryExpression)?.left?.matchesNameReference(parameterName) == true
            }
            assignment?.replaceIt(/* precomputedReplacement = */ null, /* preserveAssignmentWhenReplacing = */ true)
                ?: kotlinOptionsBlock.addExpressionIfMissing(parameterNameAndValueExpression)
        } else {
            if (projectSupportsCompilerOptions(this, kotlinVersion)) {
                addOptionToCompilerOptions(taskName, parameterName, parameterNameAndValueExpression, replaceIt)
            } else {
                // Add kotlinOptions
                addImportIfMissing("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                script?.blockExpression?.addDeclarationIfMissing("val $taskName: KotlinCompile by tasks")
                addTopLevelBlock("$taskName.kotlinOptions")?.addExpressionIfMissing(parameterNameAndValueExpression)
            }
        }
    }

    /**
     * Returns `true` if [this] is a [KtNameReferenceExpression] referring to [name], `false` otherwise.
     */
    private fun KtExpression.matchesNameReference(name: String): Boolean =
        (this as? KtNameReferenceExpression)?.getReferencedName() == name

    /**
     * If [this] is a [KtSimpleNameExpression] (a name reference or an operation reference),
     * returns its referenced name; otherwise returns `null`.
     */
    private fun KtExpression.referencedNameOrNull(): String? =
        (this as? KtSimpleNameExpression)?.getReferencedName()

    /**
     * Returns `true` if [this] mentions a [KtSimpleNameExpression] with the given [name] anywhere in its
     * PSI subtree (including [this] itself). Useful for matching the receiver/left-hand side of qualified
     * expressions where a variable name may appear as part of a `foo.bar` chain.
     */
    private fun KtExpression.containsNameReference(name: String): Boolean {
        return this.referencedNameOrNull() == name ||
                PsiTreeUtil.findChildrenOfType(this, KtSimpleNameExpression::class.java)
                    .any { it.getReferencedName() == name }
    }

    private fun KtFile.addOptionToCompilerOptions(
        taskName: String,
        parameterName: String,
        parameterNameAndValueExpression: KtExpression,
        replaceIt: KtExpression.(/* precomputedReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement? {
        // Derive the right-hand side from PSI: for an assignment like `param = value`, take the value side;
        // otherwise fall back to the whole expression.
        val parameterValueExpression = (parameterNameAndValueExpression as? KtBinaryExpression)?.right
            ?: parameterNameAndValueExpression
        val parameterValueText = parameterValueExpression.text
        val compilerOption = getCompilerOption(parameterName, parameterValueText)
        compilerOption.classToImport?.let {
            addImportIfMissing(it.toString())
        }
        var compilerOptionsBlock = findScriptInitializer("$taskName.$COMPILER_OPTIONS")?.getBlock()
        if (compilerOptionsBlock == null) {
            compilerOptionsBlock = findScriptInitializer("kotlin")?.getBlock()?.findBlock(COMPILER_OPTIONS)
            if (compilerOptionsBlock == null) {
                val scriptInitializer = findScriptInitializer("tasks.withType<KotlinCompile>")
                    ?: findScriptInitializer("tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>")
                compilerOptionsBlock = scriptInitializer?.getBlock()?.findBlock(COMPILER_OPTIONS)
            }
        }
        return if (compilerOptionsBlock == null) {
            addCompilerOptionsBlockAndOption(taskName, compilerOption)
        } else {
            replaceOrAddCompilerOption(compilerOptionsBlock, parameterName, compilerOption, replaceIt)
        }
    }

    private fun KtFile.addCompilerOptionsBlockAndOption(taskName: String, compilerOption: CompilerOption): KtExpression? {
        addImportIfMissing("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
        script?.blockExpression?.addDeclarationIfMissing("val $taskName: KotlinCompile by tasks")
        val compilerOptionsBlock = addTopLevelBlock("$taskName.compilerOptions")
        return compilerOptionsBlock?.addExpressionIfMissing(compilerOption.expression)
    }

    private fun replaceOrAddCompilerOption(
        compilerOptionsBlock: KtBlockExpression, parameterName: String, compilerOption: CompilerOption,
        replaceIt: KtExpression.(/* precomputedReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement {
        var precomputedReplacement = compilerOption.expression
        var preserveAssignmentWhenReplacing = true
        val assignment: KtExpression? = compilerOptionsBlock.statements.find { stmt ->
            when (stmt) {
                is KtDotQualifiedExpression -> {
                    preserveAssignmentWhenReplacing = false
                    stmt.receiverExpression.matchesNameReference(parameterName)
                }

                is KtBinaryExpression -> {
                    if (stmt.left?.matchesNameReference(parameterName) == true) {
                        compilerOption.compilerOptionValue?.let {
                            precomputedReplacement = "$parameterName = $it"
                        }
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    false
                }
            }
        }
        return if (assignment != null) {
            assignment.replaceIt(precomputedReplacement, preserveAssignmentWhenReplacing)
        } else {
            compilerOptionsBlock.addExpressionIfMissing(compilerOption.expression)
        }
    }

    private fun KtFile.changeKotlinTaskParameter(
        parameterName: String,
        parameterValue: String,
        forTests: Boolean,
        kotlinVersion: IdeKotlinVersion? = null
    ): PsiElement? {
        val parameterNameAndValueExpression = psiFactory.createExpression("$parameterName = \"$parameterValue\"")
        return addOrReplaceKotlinTaskParameter(parameterName, parameterNameAndValueExpression, forTests, kotlinVersion) { replacement, _ ->
            if (replacement != null) {
                replace(psiFactory.createExpression(replacement))
            } else {
                replace(psiFactory.createExpression("$parameterName = \"$parameterValue\""))
            }
        }
    }

    private fun KtBlockExpression.getRepositorySnippet(version: IdeKotlinVersion): String? {
        val repository = getRepositoryForVersion(version)
        return when {
            repository != null -> repository.toKotlinRepositorySnippet()
            !isRepositoryConfigured(text) -> MAVEN_CENTRAL
            else -> null
        }
    }

    private fun KtFile.getBuildScriptBlock(): KtBlockExpression? = findOrCreateScriptInitializer("buildscript", true)

    private fun KtFile.findOrCreateScriptInitializer(name: String, first: Boolean = false): KtBlockExpression? =
        findScriptInitializer(name)?.getBlock() ?: addTopLevelBlock(name, first)

    private fun KtBlockExpression.getRepositoriesBlock(): KtBlockExpression? = findOrCreateBlock("repositories")

    private fun KtBlockExpression.getDependenciesBlock(): KtBlockExpression? = findOrCreateBlock("dependencies")

    private fun KtBlockExpression.addRepositoryIfMissing(version: IdeKotlinVersion): KtCallExpression? {
        val snippet = getRepositorySnippet(version) ?: return null
        return addExpressionIfMissing(snippet) as? KtCallExpression
    }

    private fun KtBlockExpression.addMavenCentralIfMissing(): KtCallExpression? =
        if (!isRepositoryConfigured(text)) addExpressionIfMissing(MAVEN_CENTRAL) as? KtCallExpression else null

    private fun KtBlockExpression.findOrCreateBlock(name: String, first: Boolean = false) = findBlock(name) ?: addBlock(name, first)

    private fun KtBlockExpression.addPluginToClassPathIfMissing(): KtCallExpression? =
        addExpressionIfMissing(getKotlinGradlePluginClassPathSnippet()) as? KtCallExpression

    private fun KtBlockExpression.addBlock(name: String, first: Boolean = false): KtBlockExpression? {
        return psiFactory.createExpression("$name {\n}")
            .let { if (first) addAfter(it, null) else add(it) }
            ?.apply { addNewLinesIfNeeded() }
            ?.cast<KtCallExpression>()
            ?.getBlock()
    }

    private fun KtFile.addTopLevelBlock(name: String, first: Boolean = false): KtBlockExpression? {
        val scriptInitializer = psiFactory.createScriptInitializer("$name {\n}")
        val addedElement = addToScriptBlock(scriptInitializer, first) as? KtScriptInitializer
        addedElement?.addNewLinesIfNeeded()
        return addedElement?.getBlock()
    }

    private fun PsiElement.addSibling(element: PsiElement): PsiElement = parent.addAfter(element, this)

    private fun PsiElement.addNewLineBefore(lineBreaks: Int = 1) {
        parent.addBefore(psiFactory.createNewLine(lineBreaks), this)
    }

    private fun PsiElement.addNewLineAfter(lineBreaks: Int = 1) {
        parent.addAfter(psiFactory.createNewLine(lineBreaks), this)
    }

    private fun PsiElement.addNewLinesIfNeeded(lineBreaks: Int = 1) {
        if (prevSibling != null && prevSibling !is PsiWhiteSpace) {
            addNewLineBefore(lineBreaks)
        }

        if (nextSibling != null && nextSibling !is PsiWhiteSpace) {
            addNewLineAfter(lineBreaks)
        }
    }

    private fun KtFile.addToScriptBlock(element: PsiElement, first: Boolean = false): PsiElement? =
        if (first) script?.blockExpression?.addAfter(element, null) else script?.blockExpression?.add(element)

    private fun KtFile.addImportIfMissing(path: String): KtImportDirective =
        importDirectives.find { it.importPath?.pathStr == path } ?: importList?.add(
            psiFactory.createImportDirective(
                ImportPath.fromString(
                    path
                )
            )
        ) as KtImportDirective

    private fun KtBlockExpression.addExpressionAfterIfMissing(text: String, after: PsiElement): KtExpression = addStatementIfMissing(text) {
        addAfter(psiFactory.createExpression(it), after)
    }

    internal fun KtBlockExpression.addExpressionIfMissing(text: String, first: Boolean = false): KtExpression =
        addStatementIfMissing(text) {
            psiFactory.createExpression(it).let { created ->
                if (first) addAfter(created, null) else add(created)
            }
        }

    /**
     * PSI-based overload of [addExpressionIfMissing]. Looks for an existing statement that is structurally
     * equal (ignoring whitespace) to [expression] and returns it; otherwise inserts a copy of [expression].
     */
    private fun KtBlockExpression.addExpressionIfMissing(expression: KtExpression, first: Boolean = false): KtExpression =
        addExpressionIfMissing(expression.text, first)

    private fun KtBlockExpression.addDeclarationIfMissing(text: String, first: Boolean = false): KtDeclaration =
        addStatementIfMissing(text) {
            psiFactory.createDeclaration<KtDeclaration>(it).let { created ->
                if (first) addAfter(created, null) else add(created)
            }
        }

    private inline fun <reified T : PsiElement> KtBlockExpression.addStatementIfMissing(
        text: String,
        crossinline factory: (String) -> PsiElement
    ): T {
        statements.find { StringUtil.equalsIgnoreWhitespaces(it.text, text) }?.let {
            return it as T
        }

        return factory(text).apply { addNewLinesIfNeeded() } as T
    }

    private fun KtPsiFactory.createScriptInitializer(text: String): KtScriptInitializer =
        createFile("dummy.kts", text).script?.blockExpression?.firstChild as KtScriptInitializer

    private val PsiElement.psiFactory: KtPsiFactory
        get() = KtPsiFactory(project)

    internal fun getCompileDependencySnippet(
        groupId: String,
        artifactId: String,
        version: String?,
        compileScope: String = IMPLEMENTATION
    ): String {
        if (groupId != KOTLIN_GROUP_ID) {
            return "$compileScope(\"$groupId:$artifactId:$version\")"
        }

        val kotlinPluginName =
            if (scriptFile.module?.buildSystemType == BuildSystemType.AndroidGradle) {
                "kotlin-android"
            } else {
                KotlinGradleModuleConfigurator.KOTLIN
            }

        if (useNewSyntax(kotlinPluginName, gradleVersion)) {
            return "$compileScope(${getKotlinModuleDependencySnippet(artifactId)})"
        }

        val libraryVersion = if (version == GSK_KOTLIN_VERSION_PROPERTY_NAME) "$$version" else version
        return "$compileScope(${getKotlinModuleDependencySnippet(artifactId, libraryVersion)})"
    }

    companion object {
        const val GSK_KOTLIN_VERSION_PROPERTY_NAME: String = "kotlin_version"

        fun getKotlinGradlePluginClassPathSnippet(): String =
            "classpath(${getKotlinModuleDependencySnippet("gradle-plugin", "$$GSK_KOTLIN_VERSION_PROPERTY_NAME")})"

        fun getKotlinModuleDependencySnippet(artifactId: String, version: String? = null): String {
            val moduleName = artifactId.removePrefix("kotlin-")
            return when (version) {
                null -> "kotlin(\"$moduleName\")"
                "$$GSK_KOTLIN_VERSION_PROPERTY_NAME" -> "kotlinModule(\"$moduleName\", $GSK_KOTLIN_VERSION_PROPERTY_NAME)"
                else -> "kotlinModule(\"$moduleName\", ${"\"$version\""})"
            }
        }
    }
}

private fun KtExpression.isTargetSourceSetDeclaration(moduleName: String): Boolean = with(text) {
    when (this@isTargetSourceSetDeclaration) {
        is KtProperty -> startsWith("val $moduleName by") || initializer?.isTargetSourceSetDeclaration(moduleName) == true
        is KtCallExpression -> startsWith("getByName(\"$moduleName\")")
        else -> false
    }
}
