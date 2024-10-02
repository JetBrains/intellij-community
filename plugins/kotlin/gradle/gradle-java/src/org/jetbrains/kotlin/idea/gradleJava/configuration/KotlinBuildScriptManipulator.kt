// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.buildArgumentString
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.replaceLanguageFeature
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.CompilerOption
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getCompilerOption
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.kotlinVersionIsEqualOrHigher
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.utils.addToStdlib.cast

class KotlinBuildScriptManipulator(
    override val scriptFile: KtFile,
    override val preferNewSyntax: Boolean
) : GradleBuildScriptManipulator<KtFile> {
    override fun isApplicable(file: PsiFile): Boolean = file is KtFile

    private val gradleVersion = GradleVersionProvider.fetchGradleVersion(scriptFile)

    override fun isConfiguredWithOldSyntax(kotlinPluginName: String) = runReadAction {
        scriptFile.containsApplyKotlinPlugin(kotlinPluginName) && scriptFile.containsCompileStdLib()
    }

    override fun isConfigured(kotlinPluginExpression: String): Boolean = runReadAction {
        scriptFile.containsKotlinPluginInPluginsGroup(kotlinPluginExpression) && scriptFile.containsCompileStdLib()
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

    override fun configureSettingsFile(pluginName: String, version: IdeKotlinVersion): Boolean {
        val originalText = scriptFile.text
        scriptFile.getOrCreatePluginManagementBlock()?.findOrCreateBlock("plugins")?.let {
            if (it.findPluginInPluginsGroup(pluginName) == null) {
                it.addExpressionIfMissing(
                    "$pluginName version \"${version.artifactVersion}\""
                ) as? KtCallExpression
            }
        }
        return originalText != scriptFile.text
    }

    override fun getKotlinVersionFromBuildScript(): IdeKotlinVersion? {
        return scriptFile.getKotlinVersion()
    }

    override fun hasExplicitlyDefinedKotlinVersion(): Boolean {
        val pluginsBlock = scriptFile.findScriptInitializer("plugins")?.getBlock() ?: return false
        return pluginsBlock.findPluginExpressions(::isKotlinPluginIdentifier)?.versionExpression != null
    }

    override fun findAndRemoveKotlinVersionFromBuildScript(): Boolean {
        val pluginsBlock = scriptFile.findScriptInitializer("plugins")?.getBlock() ?: return false
        val pluginExpression = pluginsBlock.findPluginExpressions(::isKotlinPluginIdentifier)
        pluginExpression?.versionExpression?.let {
            it.delete()
            return true
        }
        return false
    }

    override fun PsiElement.findParentBlock(name: String): PsiElement? {
        val parent = PsiTreeUtil.findFirstParent(this) { elem ->
            (elem is KtCallExpression && elem.calleeExpression?.text?.contains(name) == true) ||
                    (elem is KtDotQualifiedExpression && elem.text?.contains(name) == true)
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
            .filter { it.left?.text?.contains(variableName) == true && it.operationReference.text == "=" }

        val setterName = "set${variableName.capitalize()}"
        val setterCalls = PsiTreeUtil.findChildrenOfType(this, KtCallExpression::class.java)
            .filter { it.calleeExpression?.text == setterName && it.valueArguments.size == 1 }

        val propertyCalls = PsiTreeUtil.findChildrenOfType(this, KtDotQualifiedExpression::class.java)
            .mapNotNull { stmt ->
                if (!stmt.receiverExpression.text.contains(variableName)) return@mapNotNull null
                val callExpression = stmt.selectorExpression as? KtCallExpression ?: return@mapNotNull null
                if (callExpression.calleeExpression?.text?.contains("set") == true && callExpression.valueArguments.size == 1) {
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
        stdlibArtifactName: String,
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
                getDependenciesBlock()?.addNoVersionCompileStdlibIfMissing(stdlibArtifactName)
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
                getDependenciesBlock()?.addCompileStdlibIfMissing(stdlibArtifactName)
            }
            getRepositoriesBlock()?.apply {
                addRepositoryIfMissing(version)
                addMavenCentralIfMissing()
            }

            configureToolchainOrKotlinCompilerOptions(jvmTarget, version, gradleVersion, changedFiles)
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
        if (targetModule != null && targetModule.isMultiPlatformModule) {
            if (addKotlinMultiplatformDependencyWithConventionSourceSets(
                    targetModule, scope,
                    libraryDescriptor.libraryGroupId, libraryDescriptor.libraryArtifactId,
                    libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
                )
            ) return
        }

        val dependencyText = getCompileDependencySnippet(
            libraryDescriptor.libraryGroupId,
            libraryDescriptor.libraryArtifactId,
            libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion,
            scope.toGradleCompileScope(targetModule)
        )

        if (targetModule != null && usesNewMultiplatform()) {
            val findOrCreateTargetSourceSet = scriptFile
                .getKotlinBlock()
                ?.getSourceSetsBlock()
                ?.findOrCreateTargetSourceSet(targetModule.name.takeLastWhile { it != '.' })
            val dependenciesBlock = findOrCreateTargetSourceSet?.getDependenciesBlock()
            dependenciesBlock?.addExpressionIfMissing(dependencyText)
        } else {
            scriptFile.getDependenciesBlock()?.addExpressionIfMissing(dependencyText)
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

    override fun getKotlinStdlibVersion(): String? = scriptFile.getKotlinStdlibVersion()

    override fun addFoojayPlugin(changedFiles: ChangedConfiguratorFiles) {
        val settingsFile = scriptFile.module?.let {
            it.getTopLevelBuildScriptSettingsPsiFile() as? KtFile
        } ?: return
        changedFiles.storeOriginalFileContent(settingsFile)

        if (!settingsFile.canBeConfigured()) {
            return
        }
        addFoojayPlugin(settingsFile)
    }

    override fun addFoojayPlugin(settingsFile: PsiFile) {
        if (settingsFile !is KtFile) return
        val pluginBlock = settingsFile.getSettingsPluginsBlock() ?: return
        if (pluginBlock.findPluginInPluginsGroup("id(\"$FOOJAY_RESOLVER_NAME\")") != null) return
        if (pluginBlock.findPluginInPluginsGroup("id(\"$FOOJAY_RESOLVER_CONVENTION_NAME\")") != null) return
        val foojayVersion = Versions.GRADLE_PLUGINS.FOOJAY_VERSION
        pluginBlock.addExpressionIfMissing("id(\"$FOOJAY_RESOLVER_CONVENTION_NAME\") version \"$foojayVersion\"")
    }

    private fun KtBlockExpression.addCompileStdlibIfMissing(stdlibArtifactName: String): KtCallExpression? =
        findStdLibDependency()
            ?: addExpressionIfMissing(
                getCompileDependencySnippet(
                    KOTLIN_GROUP_ID,
                    stdlibArtifactName,
                    version = "\$$GSK_KOTLIN_VERSION_PROPERTY_NAME"
                )
            ) as? KtCallExpression

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

    private fun KtBlockExpression.addNoVersionCompileStdlibIfMissing(stdlibArtifactName: String): KtCallExpression? =
        findStdLibDependency() ?: addExpressionIfMissing(
            "implementation(${
                getKotlinModuleDependencySnippet(
                    stdlibArtifactName,
                    null
                )
            })"
        ) as? KtCallExpression

    private fun KtFile.containsCompileStdLib(): Boolean =
        findScriptInitializer("dependencies")?.getBlock()?.findStdLibDependency() != null

    private fun KtFile.containsApplyKotlinPlugin(pluginName: String): Boolean =
        findScriptInitializer("apply")?.getBlock()?.findPlugin(pluginName) != null

    private fun KtFile.containsKotlinPluginInPluginsGroup(pluginName: String): Boolean =
        findScriptInitializer("plugins")?.getBlock()?.findPluginInPluginsGroup(pluginName) != null

    private fun KtBlockExpression.findPlugin(pluginName: String): KtCallExpression? {
        return PsiTreeUtil.getChildrenOfType(this, KtCallExpression::class.java)?.find {
            (it.calleeExpression?.text == "plugin" ||
                    it.calleeExpression?.text == "id") &&
                    it.valueArguments.firstOrNull()?.text == "\"$pluginName\""
        }
    }

    private fun KtBlockExpression.findClassPathDependencyVersion(pluginName: String): String? {
        return PsiTreeUtil.getChildrenOfAnyType(this, KtCallExpression::class.java).mapNotNull {
            if (it?.calleeExpression?.text == "classpath") {
                val dependencyName = it.valueArguments.firstOrNull()?.text?.extractStringValue()
                if (dependencyName?.startsWith(pluginName) == true) dependencyName.substringAfter("$pluginName:") else null
            } else null
        }.singleOrNull()
    }

    private fun getPluginInfoFromBuildScript(
        operatorName: String?,
        pluginVersion: KtExpression?,
        receiverCalleeExpression: KtCallExpression?
    ): Pair<String, String>? {
        val receiverCalleeExpressionText = receiverCalleeExpression?.calleeExpression?.text?.trim()
        val receivedPluginName = when {
            receiverCalleeExpressionText == "id" ->
                receiverCalleeExpression.valueArguments.firstOrNull()?.text?.trim()?.extractStringValue()

            operatorName == "version" -> receiverCalleeExpressionText
            else -> null
        }
        val pluginVersionText = pluginVersion?.text?.trim()?.extractStringValue() ?: return null

        return receivedPluginName?.to(pluginVersionText)
    }

    private fun KtBlockExpression.findPluginVersionInPluginGroup(pluginName: String): String? {
        val versionsToPluginNames =
            PsiTreeUtil.getChildrenOfAnyType(this, KtBinaryExpression::class.java, KtDotQualifiedExpression::class.java).mapNotNull {
                when (it) {
                    is KtBinaryExpression -> getPluginInfoFromBuildScript(
                        it.operationReference.text,
                        it.right,
                        it.left as? KtCallExpression
                    )

                    is KtDotQualifiedExpression ->
                        (it.selectorExpression as? KtCallExpression)?.run {
                            getPluginInfoFromBuildScript(
                                calleeExpression?.text,
                                valueArguments.firstOrNull()?.getArgumentExpression(),
                                it.receiverExpression as? KtCallExpression
                            )
                        }

                    else -> null
                }
            }.toMap()
        return versionsToPluginNames.getOrDefault(pluginName, null)
    }

    private fun String.extractStringValue(): String {
        // Two steps because we want to keep parenthesis inside the string
        return trim('(', ')', ' ', '\t').trim('"')
    }

    private fun KtBlockExpression.findPluginInPluginsGroup(pluginName: String): PluginExpression? {
        return findPluginExpressions { methodName, arguments ->
            val firstArgument = arguments.singleOrNull() ?: return@findPluginExpressions false
            "${methodName}(${firstArgument.text})" == pluginName
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
         * To delete more than one part you will have to parse the method call chain again and call delete on the new chain!
         */
        val delete: () -> Unit
    )

    internal fun KtExpression.parsePluginCallChain(): List<ChainedMethodCallPart>? {
        return when (this) {
            is KtBinaryExpression -> {
                val methodName = operationReference.text.trim()
                val leftCallChain = left?.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, listOf(right ?: return null)) {
                    left?.let {
                        this.replace(it)
                    }
                }
            }

            is KtDotQualifiedExpression -> {
                val selectorExpression = selectorExpression as? KtCallExpression ?: return null
                val methodName = selectorExpression.calleeExpression?.text?.trim() ?: return null
                val arguments = selectorExpression.valueArguments.mapNotNull { it.getArgumentExpression() }
                val leftCallChain = receiverExpression.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, arguments) {
                    this.replace(receiverExpression)
                }
            }

            is KtCallExpression -> {
                val methodName = (calleeExpression as? KtNameReferenceExpression)?.text?.trim() ?: return null
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
        val firstArgumentText = arguments.singleOrNull()?.text?.extractStringValue() ?: return false
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
            parsedVersion = IdeKotlinVersion.opt(versionExpression.text.extractStringValue())
        )
    }

    private fun KtFile.findScriptInitializer(startsWith: String): KtScriptInitializer? =
        PsiTreeUtil.findChildrenOfType(this, KtScriptInitializer::class.java).find { it.text.startsWith(startsWith) }

    private fun KtBlockExpression.findBlock(name: String): KtBlockExpression? {
        return getChildrenOfType<KtCallExpression>().find {
            it.calleeExpression?.text == name &&
                    it.valueArguments.singleOrNull()?.getArgumentExpression() is KtLambdaExpression
        }?.getBlock()
    }

    internal fun KtScriptInitializer.getBlock(): KtBlockExpression? =
        PsiTreeUtil.findChildOfType(this, KtCallExpression::class.java)?.getBlock()

    internal fun KtCallExpression.getBlock(): KtBlockExpression? =
        (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
            ?: lambdaArguments.lastOrNull()?.getLambdaExpression()?.bodyExpression

    private fun KtFile.getKotlinStdlibVersion(): String? {
        return findScriptInitializer("dependencies")?.getBlock()?.let {
            when (val expression = it.findStdLibDependency()?.valueArguments?.firstOrNull()?.getArgumentExpression()) {
                is KtCallExpression -> expression.valueArguments.getOrNull(1)?.text?.trim('\"')
                is KtStringTemplateExpression -> expression.text?.trim('\"')?.substringAfterLast(":")?.removePrefix("$")
                else -> null
            }
        }
    }

    private fun KtBlockExpression.findStdLibDependency(): KtCallExpression? {
        return PsiTreeUtil.getChildrenOfType(this, KtCallExpression::class.java)?.find {
            val calleeText = it.calleeExpression?.text
            calleeText in SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
                    && (it.valueArguments.firstOrNull()?.getArgumentExpression()?.isKotlinStdLib() ?: false)
        }
    }

    private fun KtExpression.isKotlinStdLib(): Boolean = when (this) {
        is KtCallExpression -> {
            val calleeText = calleeExpression?.text
            (calleeText == "kotlinModule" || calleeText == "kotlin") &&
                    valueArguments.firstOrNull()?.getArgumentExpression()?.text?.startsWith("\"stdlib") ?: false
        }

        is KtStringTemplateExpression -> text.startsWith("\"$STDLIB_ARTIFACT_PREFIX")
        else -> false
    }

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
        version: IdeKotlinVersion
    ) {
        getPluginsBlock()?.let {
            val existingPluginDefinition = it.findPluginInPluginsGroup(pluginName)
            if (existingPluginDefinition?.applyExpression != null) {
                // Cannot properly handle apply, delete and redo
                existingPluginDefinition.entireExpression.delete()
            }
            if (existingPluginDefinition?.applyExpression != null || existingPluginDefinition?.versionExpression == null) {
                it.addExpressionIfMissing(
                    if (addVersion) {
                        "$pluginName version \"${version.artifactVersion}\""
                    } else pluginName
                )
            }
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

    private fun KtFile.changeCoroutineConfiguration(coroutineOption: String): PsiElement? {
        val snippet = "experimental.coroutines = Coroutines.${coroutineOption.toUpperCase()}"
        val kotlinBlock = getKotlinBlock() ?: return null
        addImportIfMissing("org.jetbrains.kotlin.gradle.dsl.Coroutines")
        val statement = kotlinBlock.statements.find { it.text.startsWith("experimental.coroutines") }
        return if (statement != null) {
            statement.replace(psiFactory.createExpression(snippet))
        } else {
            kotlinBlock.add(psiFactory.createExpression(snippet)).apply { addNewLinesIfNeeded() }
        }
    }

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
        return addOrReplaceKotlinTaskParameter(
            parameterName,
            "listOf(\"$featureArgumentString\")",
            forTests
        ) { _, preserveAssignmentWhenReplacing ->
            val prefix: String
            val postfix: String
            if (preserveAssignmentWhenReplacing) {
                prefix = "$parameterName = listOf("
                postfix = ")"
            } else {
                prefix = "$parameterName.addAll(listOf("
                postfix = "))"
            }
            val newText = text.replaceLanguageFeature(
                feature,
                state,
                kotlinVersion,
                prefix,
                postfix
            )
            replace(psiFactory.createExpression(newText))
        }
    }

    private fun KtFile.getKotlinVersion(): IdeKotlinVersion? {
        val pluginsBlock = findScriptInitializer("plugins")?.getBlock()
        val rawKotlinVersion = pluginsBlock?.findPluginVersionInPluginGroup("kotlin")
            ?: pluginsBlock?.findPluginVersionInPluginGroup("org.jetbrains.kotlin.jvm")
            ?: findScriptInitializer("buildscript")?.getBlock()?.findBlock("dependencies")
                ?.findClassPathDependencyVersion("org.jetbrains.kotlin:kotlin-gradle-plugin")
        return rawKotlinVersion?.let(IdeKotlinVersion::opt)
    }

    private fun KtBlockExpression.addParameterAssignment(
        parameterName: String,
        parameterValue: String,
        replaceIt: KtExpression.() -> PsiElement
    ): PsiElement {
        return statements.filterIsInstance<KtBinaryExpression>().firstOrNull { stmt ->
            stmt.left?.text == parameterName
        }?.replaceIt() ?: addExpressionIfMissing("$parameterName = \"$parameterValue\"")
    }

    private fun projectSupportsCompilerOptions(file: PsiFile, kotlinVersion: IdeKotlinVersion?): Boolean {
        /*
        // Current test infrastructure uses either a fallback version of Kotlin –
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
     */
    private fun KtFile.addOrReplaceKotlinTaskParameter(
        parameterName: String,
        parameterValue: String,
        forTests: Boolean,
        kotlinVersion: IdeKotlinVersion? = null,
        replaceIt: KtExpression.(/* precompiledReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement? {
        val taskName = if (forTests) "compileTestKotlin" else "compileKotlin"
        val kotlinOptionsBlock = findScriptInitializer("$taskName.kotlinOptions")?.getBlock()
        // We leave deprecated `kotlinOptions` untouched, it can be updated with `kotlinOptions` to `compilerOptions` inspection
        return if (kotlinOptionsBlock != null) {
            val assignment = kotlinOptionsBlock.statements.find {
                (it as? KtBinaryExpression)?.left?.text == parameterName
            }
            assignment?.replaceIt(/* precompiledReplacement = */ null, /* preserveAssignmentWhenReplacing = */ true)
                ?: kotlinOptionsBlock.addExpressionIfMissing("$parameterName = $parameterValue")
        } else {
            if (projectSupportsCompilerOptions(this, kotlinVersion)) {
                addOptionToCompilerOptions(taskName, parameterName, parameterValue, replaceIt)
            } else {
                // Add kotlinOptions
                addImportIfMissing("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                script?.blockExpression?.addDeclarationIfMissing("val $taskName: KotlinCompile by tasks")
                addTopLevelBlock("$taskName.kotlinOptions")?.addExpressionIfMissing("$parameterName = $parameterValue")
            }
        }
    }

    private fun KtFile.addOptionToCompilerOptions(
        taskName: String,
        parameterName: String,
        parameterValue: String,
        replaceIt: KtExpression.(/* precompiledReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement? {
        val compilerOption = getCompilerOption(parameterName, parameterValue)
        compilerOption.classToImport?.let {
            addImportIfMissing(it.toString())
        }
        val compilerOptionsBlock = findScriptInitializer("$taskName.compilerOptions")?.getBlock()
        return if (compilerOptionsBlock == null) {
            addCompilerOptionsBlockAndOption(taskName, compilerOption)
        } else {
            return replaceOrAddCompilerOption(compilerOptionsBlock, parameterName, compilerOption, replaceIt)
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
        replaceIt: KtExpression.(/* precompiledReplacement */ String?, /* preserveAssignmentWhenReplacing = */ Boolean) -> PsiElement
    ): PsiElement {
        var precompiledReplacement = compilerOption.expression
        var preserveAssignmentWhenReplacing = true
        var assignment: KtExpression? = compilerOptionsBlock.statements.find { stmt ->
            when (stmt) {
                is KtDotQualifiedExpression -> {
                    preserveAssignmentWhenReplacing = false
                    stmt.receiverExpression.text == parameterName
                }

                is KtBinaryExpression -> {
                    if (stmt.left?.text == parameterName) {
                        compilerOption.compilerOptionValue?.let {
                            precompiledReplacement = "$parameterName = $it"
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
            assignment.replaceIt(precompiledReplacement, preserveAssignmentWhenReplacing)
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
        return addOrReplaceKotlinTaskParameter(parameterName, "\"$parameterValue\"", forTests, kotlinVersion) { replacement, _ ->
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
        if (prevSibling != null && prevSibling.text.isNotBlank()) {
            addNewLineBefore(lineBreaks)
        }

        if (nextSibling != null && nextSibling.text.isNotBlank()) {
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
        compileScope: String = "implementation"
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

        val libraryVersion = if (version == GSK_KOTLIN_VERSION_PROPERTY_NAME) "\$$version" else version
        return "$compileScope(${getKotlinModuleDependencySnippet(artifactId, libraryVersion)})"
    }

    companion object {
        private const val STDLIB_ARTIFACT_PREFIX = "org.jetbrains.kotlin:kotlin-stdlib"
        const val GSK_KOTLIN_VERSION_PROPERTY_NAME = "kotlin_version"

        fun getKotlinGradlePluginClassPathSnippet(): String =
            "classpath(${getKotlinModuleDependencySnippet("gradle-plugin", "\$$GSK_KOTLIN_VERSION_PROPERTY_NAME")})"

        fun getKotlinModuleDependencySnippet(artifactId: String, version: String? = null): String {
            val moduleName = artifactId.removePrefix("kotlin-")
            return when (version) {
                null -> "kotlin(\"$moduleName\")"
                "\$$GSK_KOTLIN_VERSION_PROPERTY_NAME" -> "kotlinModule(\"$moduleName\", $GSK_KOTLIN_VERSION_PROPERTY_NAME)"
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
