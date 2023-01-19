// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableModelsProvider
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.projectConfiguration.getDefaultJvmTarget
import org.jetbrains.kotlin.idea.projectConfiguration.getJvmStdlibArtifactId
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.DEFAULT_GRADLE_PLUGIN_REPOSITORY
import org.jetbrains.kotlin.idea.configuration.LAST_SNAPSHOT_VERSION
import org.jetbrains.kotlin.idea.configuration.getRepositoryForVersion
import org.jetbrains.kotlin.idea.configuration.toGroovyRepositorySnippet
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SettingsScriptBuilder
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.scope
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX
import org.jetbrains.kotlin.idea.statistics.WizardStatsService
import org.jetbrains.kotlin.idea.statistics.WizardStatsService.ProjectCreationStats
import org.jetbrains.kotlin.idea.versions.*
import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.GradleFrameworkSupportProvider
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTextPane

abstract class GradleKotlinFrameworkSupportProvider(
    val frameworkTypeId: String,
    @Nls val displayName: String,
    val frameworkIcon: Icon
) : GradleFrameworkSupportProvider() {
    override fun getFrameworkType(): FrameworkTypeEx = object : FrameworkTypeEx(frameworkTypeId) {
        override fun getIcon(): Icon = frameworkIcon

        override fun getPresentableName(): String = displayName

        override fun createProvider(): FrameworkSupportInModuleProvider = this@GradleKotlinFrameworkSupportProvider
    }

    override fun createConfigurable(model: FrameworkSupportModel): FrameworkSupportInModuleConfigurable {
        val configurable = KotlinGradleFrameworkSupportInModuleConfigurable(model, this)
        return object : FrameworkSupportInModuleConfigurable() {
            override fun addSupport(module: Module, rootModel: ModifiableRootModel, modifiableModelsProvider: ModifiableModelsProvider) {
                configurable.addSupport(module, rootModel, modifiableModelsProvider)
            }

            override fun createComponent(): JComponent {
                val jTextPane = JTextPane()
                jTextPane.text = getDescription()
                return jTextPane
            }
        }
    }

    override fun addSupport(projectId: ProjectId,
                            module: Module,
                            rootModel: ModifiableRootModel,
                            modifiableModelsProvider: ModifiableModelsProvider,
                            buildScriptData: BuildScriptDataBuilder
    ) {
        addSupport(buildScriptData, module, rootModel.sdk, true)
    }

    open fun addSupport(
        buildScriptData: BuildScriptDataBuilder,
        module: Module,
        sdk: Sdk?,
        specifyPluginVersionIfNeeded: Boolean,
        explicitPluginVersion: IdeKotlinVersion? = null
    ) {
        var kotlinVersion = explicitPluginVersion ?: KotlinPluginLayout.standaloneCompilerVersion
        val additionalRepository = getRepositoryForVersion(kotlinVersion)
        if (kotlinVersion.isSnapshot) {
            kotlinVersion = LAST_SNAPSHOT_VERSION
        }

        val gradleVersion = buildScriptData.gradleVersion
        val useNewSyntax = gradleVersion >= MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX
        if (useNewSyntax) {
            if (additionalRepository != null) {
                val oneLineRepository = additionalRepository.toGroovyRepositorySnippet().replace('\n', ' ')
                updateSettingsScript(module) {
                    with(it) {
                        addPluginRepository(additionalRepository)
                        addMavenCentralPluginRepository()
                        addPluginRepository(DEFAULT_GRADLE_PLUGIN_REPOSITORY)
                    }
                }
                buildScriptData.addRepositoriesDefinition("mavenCentral()")
                buildScriptData.addRepositoriesDefinition(oneLineRepository)
            }

            buildScriptData.addPluginDefinitionInPluginsGroup(
                getPluginExpression() + if (specifyPluginVersionIfNeeded) " version '${kotlinVersion.artifactVersion}'" else ""
            )
        } else {
            if (additionalRepository != null) {
                val oneLineRepository = additionalRepository.toGroovyRepositorySnippet().replace('\n', ' ')
                buildScriptData.addBuildscriptRepositoriesDefinition(oneLineRepository)

                buildScriptData.addRepositoriesDefinition("mavenCentral()")
                buildScriptData.addRepositoriesDefinition(oneLineRepository)
            }

            buildScriptData
                .addPluginDefinition(KotlinWithGradleConfigurator.getGroovyApplyPluginDirective(getPluginId()))
                .addBuildscriptRepositoriesDefinition("mavenCentral()")
                .addBuildscriptPropertyDefinition("ext.kotlin_version = '${kotlinVersion.artifactVersion}'")
        }

        buildScriptData.addRepositoriesDefinition("mavenCentral()")

        for (dependency in getDependencies(sdk)) {
            buildScriptData.addDependencyNotation(
                KotlinWithGradleConfigurator.getGroovyDependencySnippet(dependency, "implementation", !useNewSyntax, gradleVersion)
            )
        }
        for (dependency in getTestDependencies()) {
            buildScriptData.addDependencyNotation(
                if (":" in dependency)
                    "${gradleVersion.scope("testImplementation")} \"$dependency\""
                else
                    KotlinWithGradleConfigurator.getGroovyDependencySnippet(dependency, "testImplementation", !useNewSyntax, gradleVersion)
            )
        }

        if (useNewSyntax) {
            updateSettingsScript(module) { updateSettingsScript(it, specifyPluginVersionIfNeeded) }
        } else {
            buildScriptData.addBuildscriptDependencyNotation(KotlinWithGradleConfigurator.CLASSPATH)
        }

        val isNewProject = module.project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == true
        if (isNewProject) {
            ProjectCodeStyleImporter.apply(module.project, KotlinStyleGuideCodeStyle.INSTANCE)
            GradlePropertiesFileFacade.forProject(module.project).addCodeStyleProperty(KotlinStyleGuideCodeStyle.CODE_STYLE_SETTING)
        }
        //KotlinCreateActionsFUSCollector.logProjectTemplate("Gradle", this.presentableName)
        val projectCreationStats = ProjectCreationStats("Gradle", this.presentableName, "gradleGroovy")

        WizardStatsService.logDataOnProjectGenerated(session = null, module.project, projectCreationStats)
    }

    protected open fun updateSettingsScript(settingsBuilder: SettingsScriptBuilder<out PsiFile>, specifyPluginVersionIfNeeded: Boolean) {}

    protected abstract fun getDependencies(sdk: Sdk?): List<String>
    protected open fun getTestDependencies(): List<String> = listOf()

    @NonNls
    protected abstract fun getPluginId(): String

    @NonNls
    protected abstract fun getPluginExpression(): String

    @Nls
    protected abstract fun getDescription(): String
}

open class GradleKotlinJavaFrameworkSupportProvider(
    frameworkTypeId: String = "KOTLIN",
    @Nls displayName: String = KotlinIdeaGradleBundle.message("framework.support.provider.kotlin.jvm.display.name")
) : GradleKotlinFrameworkSupportProvider(frameworkTypeId, displayName, KotlinIcons.SMALL_LOGO) {

    override fun getPluginId() = KotlinGradleModuleConfigurator.KOTLIN
    override fun getPluginExpression() = "id 'org.jetbrains.kotlin.jvm'"

    override fun getDependencies(sdk: Sdk?): List<String> {
        return listOf(getJvmStdlibArtifactId(sdk, KotlinPluginLayout.standaloneCompilerVersion))
    }

    override fun addSupport(
        buildScriptData: BuildScriptDataBuilder,
        module: Module,
        sdk: Sdk?,
        specifyPluginVersionIfNeeded: Boolean,
        explicitPluginVersion: IdeKotlinVersion?
    ) {
        super.addSupport(buildScriptData, module, sdk, specifyPluginVersionIfNeeded, explicitPluginVersion)
        val jvmTarget = getDefaultJvmTarget(sdk, KotlinPluginLayout.standaloneCompilerVersion)
        if (jvmTarget != null) {
            val description = jvmTarget.description
            buildScriptData.addOther("compileKotlin {\n    kotlinOptions.jvmTarget = \"$description\"\n}\n\n")
            buildScriptData.addOther("compileTestKotlin {\n    kotlinOptions.jvmTarget = \"$description\"\n}\n")
        }
    }

    override fun getDescription() =
        KotlinIdeaGradleBundle.message("description.text.a.single.platform.kotlin.library.or.application.targeting.the.jvm")
}

abstract class GradleKotlinJSFrameworkSupportProvider(
    frameworkTypeId: String,
    @Nls displayName: String
) : GradleKotlinFrameworkSupportProvider(frameworkTypeId, displayName, KotlinIcons.JS) {
    abstract val jsSubTargetName: String

    override fun addSupport(
        buildScriptData: BuildScriptDataBuilder,
        module: Module,
        sdk: Sdk?,
        specifyPluginVersionIfNeeded: Boolean,
        explicitPluginVersion: IdeKotlinVersion?
    ) {
        super.addSupport(buildScriptData, module, sdk, specifyPluginVersionIfNeeded, explicitPluginVersion)

        buildScriptData.addOther(
            """
                kotlin {
                    js {
                        $jsSubTargetName {
                        """.trimIndent() +
                    (
                            additionalSubTargetSettings()
                                ?.lines()
                                ?.joinToString("\n", "\n", "\n") { line ->
                                    if (line.isBlank()) {
                                        line
                                    } else {
                                        line
                                            .prependIndent()
                                            .prependIndent()
                                            .prependIndent()
                                    }
                                } ?: "\n"
                            ) +
                    """
                        }
                        binaries.executable()
                    }
                }
            """.trimIndent()
        )
    }

    abstract fun additionalSubTargetSettings(): String?

    override fun getPluginId() = "kotlin2js"
    override fun getPluginExpression() = "id 'org.jetbrains.kotlin.js'"
    override fun getDependencies(sdk: Sdk?) = listOf(MAVEN_JS_STDLIB_ID)
    override fun getTestDependencies() = listOf(MAVEN_JS_TEST_ID)
    override fun getDescription() =
        KotlinIdeaGradleBundle.message("description.text.a.single.platform.kotlin.library.or.application.targeting.javascript")
}

open class GradleKotlinJSBrowserFrameworkSupportProvider(
    frameworkTypeId: String = "KOTLIN_JS_BROWSER",
    @Nls displayName: String = KotlinIdeaGradleBundle.message("framework.support.provider.kotlin.js.for.browser.display.name")
) : GradleKotlinJSFrameworkSupportProvider(frameworkTypeId, displayName) {
    override val jsSubTargetName: String
        get() = "browser"

    override fun getDescription() =
        KotlinIdeaGradleBundle.message("description.text.a.single.platform.kotlin.library.or.application.targeting.js.for.browser")

    override fun addSupport(
        buildScriptData: BuildScriptDataBuilder,
        module: Module,
        sdk: Sdk?,
        specifyPluginVersionIfNeeded: Boolean,
        explicitPluginVersion: IdeKotlinVersion?
    ) {
        super.addSupport(buildScriptData, module, sdk, specifyPluginVersionIfNeeded, explicitPluginVersion)
        addBrowserSupport(module)
    }

    override fun additionalSubTargetSettings(): String? =
        browserConfiguration(kotlinDsl = false)
}

open class GradleKotlinJSNodeFrameworkSupportProvider(
    frameworkTypeId: String = "KOTLIN_JS_NODE",
    @Nls displayName: String = KotlinIdeaGradleBundle.message("framework.support.provider.kotlin.js.for.node.js.display.name")
) : GradleKotlinJSFrameworkSupportProvider(frameworkTypeId, displayName) {
    override val jsSubTargetName: String
        get() = "nodejs"

    override fun additionalSubTargetSettings(): String? =
        null

    override fun getDescription() =
        KotlinIdeaGradleBundle.message("description.text.a.single.platform.kotlin.library.or.application.targeting.js.for.node.js")
}

open class GradleKotlinMPPFrameworkSupportProvider : GradleKotlinFrameworkSupportProvider(
    "KOTLIN_MPP", KotlinIdeaGradleBundle.message("display.name.kotlin.multiplatform"), KotlinIcons.MPP
) {
    override fun getPluginId() = "org.jetbrains.kotlin.multiplatform"
    override fun getPluginExpression() = "id 'org.jetbrains.kotlin.multiplatform'"

    override fun getDependencies(sdk: Sdk?): List<String> = listOf()
    override fun getTestDependencies(): List<String> = listOf()

    override fun getDescription() =
        KotlinIdeaGradleBundle.message("description.text.multi.targeted.jvm.js.ios.etc.project.with.shared.code.in.common.modules")
}

open class GradleKotlinMPPSourceSetsFrameworkSupportProvider : GradleKotlinMPPFrameworkSupportProvider() {

    override fun addSupport(
        buildScriptData: BuildScriptDataBuilder,
        module: Module,
        sdk: Sdk?,
        specifyPluginVersionIfNeeded: Boolean,
        explicitPluginVersion: IdeKotlinVersion?
    ) {
        super.addSupport(buildScriptData, module, sdk, specifyPluginVersionIfNeeded, explicitPluginVersion)

        val projectCreationStats = ProjectCreationStats("Gradle", this.presentableName + " as framework", "gradleGroovy")
        WizardStatsService.logDataOnProjectGenerated(session = null, module.project, projectCreationStats)

        buildScriptData.addOther(
            """kotlin {
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    sourceSets {
        commonMain {
            dependencies {
                implementation kotlin('stdlib-common')
            }
        }
        commonTest {
            dependencies {
                implementation kotlin('test-common')
                implementation kotlin('test-annotations-common')
            }
        }
    }
}"""
        )
    }
}

