// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.tree.LeafState
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.shared.definition.kotlinScriptTemplate
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.io.File
import java.util.jar.JarOutputStream
import javax.swing.Icon
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class KotlinFirScriptStructureViewTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun testScriptFileRootContainsContextAndDeclarations() {
        val definitionJar = createEmptyJar("definition-context-test.jar")
        val scriptJar = createEmptyJar("script-context-test.jar")
        registerScriptContextDefinition(definitionJar, scriptJar)

        val file = configureScript("sample.context.kts")
        loadScript(file)

        withScriptStructureView(file) { root, isRootVisible ->
            assertTrue(isRootVisible)
            assertEquals("sample.context.kts", root.presentation.presentableText)

            assertEquals(
                listOf("Script Definition", "Script Configuration", "File Structure"),
                childTexts(root),
            )

            val definitionRoot = findChild(root, "Script Definition")
            val configuration = findChild(root, "Script Configuration")

            assertEquals(
                listOf(scriptDefinitionId(file), "Definition Classpath"),
                childTexts(definitionRoot),
            )

            val definitionName = children(definitionRoot).first()
            assertSame(classNodeIcon(), definitionName.presentation.getIcon(false))
            assertEquals(scriptDefinitionId(file), definitionName.presentation.presentableText)

            assertEquals(
                listOf("JDK", "Implicit Receivers", "Provided Properties", "Default Imports", "Script Classpath"),
                childTexts(configuration),
            )
            assertTrue(childTexts(findChild(configuration, "JDK")).single().isNotBlank())

            val implicitReceiverPresentation = children(findChild(configuration, "Implicit Receivers")).first().presentation
            assertEquals("kotlin.script.templates.standard.ScriptTemplateWithArgs", implicitReceiverPresentation.presentableText)

            assertEquals(
                listOf("scriptName: kotlin.String", "value: kotlin.Int"),
                childTexts(findChild(configuration, "Provided Properties")),
            )

            val importsGroup = findChild(configuration, "Default Imports")
            assertEquals(listOf("java.io.File", "kotlin.collections.*"), childTexts(importsGroup))
            assertSame(classNodeIcon(), children(importsGroup).first().presentation.getIcon(false))
            assertEquals(LeafState.ALWAYS, (children(importsGroup).first() as LeafState.Supplier).leafState)
            assertNotSame(file, children(importsGroup).first().value)

            val definitionClasspath = findChild(definitionRoot, "Definition Classpath")
            assertEquals(listOf("definition-context-test.jar"), childTexts(definitionClasspath))
            assertEquals(presentableLocation(file.project, definitionJar.parentFile), children(definitionClasspath).single().presentation.locationString)

            val scriptClasspath = findChild(configuration, "Script Classpath")
            assertEquals(listOf("definition-context-test.jar", "script-context-test.jar"), childTexts(scriptClasspath))
            assertEquals(
                listOf(
                    presentableLocation(file.project, definitionJar.parentFile),
                    presentableLocation(file.project, scriptJar.parentFile),
                ),
                children(scriptClasspath).map { it.presentation.locationString },
            )

            assertEquals(
                listOf("userFunction(): Unit"),
                childTextsInReadAction(findChild(root, "File Structure")),
            )
        }
    }

    fun testResolvedScriptConfigurationTakesPrecedenceOverBaseDefinitionMetadata() {
        val definitionJar = createEmptyJar("definition-metadata-test.jar")
        val scriptJar = createEmptyJar("script-metadata-test.jar")
        registerScriptContextDefinition(definitionJar, scriptJar)

        val file = configureScript("sample.context.kts")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
            replaceStoredScriptConfiguration(
                file = file,
                configuration = ScriptCompilationConfiguration {
                    fileExtension("context.kts")
                    kotlinScriptTemplate {
                        title = "Resolved Context Script"
                    }
                    implicitReceivers(String::class)
                    providedProperties("value" to KotlinType(Int::class))
                    defaultImports("kotlin.io.path.*")
                    dependencies.append(JvmDependency(scriptJar))
                },
            )
        }
        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

        withScriptStructureView(file) { root, _ ->
            val definitionGroup = findChild(root, "Script Definition")
            val configuration = findChild(root, "Script Configuration")

            assertEquals(
                listOf(scriptDefinitionId(file), "Definition Classpath"),
                childTexts(definitionGroup),
            )
            assertEquals(
                listOf("JDK", "Implicit Receivers", "Provided Properties", "Default Imports", "Script Classpath"),
                childTexts(configuration),
            )
            assertEquals(listOf("null"), childTexts(findChild(configuration, "JDK")))
            assertEquals(listOf("kotlin.String"), childTexts(findChild(configuration, "Implicit Receivers")))
            assertEquals(listOf("value: kotlin.Int"), childTexts(findChild(configuration, "Provided Properties")))
            assertEquals(listOf("kotlin.io.path.*"), childTexts(findChild(configuration, "Default Imports")))
            assertEquals(listOf("definition-metadata-test.jar"), childTexts(findChild(definitionGroup, "Definition Classpath")))
            assertEquals(listOf("script-metadata-test.jar"), childTexts(findChild(configuration, "Script Classpath")))
        }
    }

    fun testScriptClasspathKeepsJarsThatAreAlsoPresentInDefinitionClasspath() {
        val sharedJar = createEmptyJar("shared-context-test.jar")
        registerScriptContextDefinition(sharedJar, sharedJar)

        val file = configureScript("sample.context.kts")
        loadScript(file)

        withScriptStructureView(file) { root, _ ->
            assertEquals(
                listOf("shared-context-test.jar"),
                childTexts(findChild(findChild(root, "Script Definition"), "Definition Classpath")),
            )
            assertEquals(
                listOf("shared-context-test.jar"),
                childTexts(findChild(findChild(root, "Script Configuration"), "Script Classpath")),
            )
        }
    }

    fun testEmptyConfigurationGroupsAreOmitted() {
        val definitionJar = createEmptyJar("definition-no-receiver-test.jar")
        registerScriptContextDefinition(definitionJar, createEmptyJar("script-no-receiver-test.jar"))

        val file = configureScript("sample.context.kts")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
            replaceStoredScriptConfiguration(
                file = file,
                configuration = ScriptCompilationConfiguration {
                    fileExtension("context.kts")
                    kotlinScriptTemplate {
                        title = "Receiverless Context Script"
                    }
                    defaultImports("kotlin.io.path.*")
                },
            )
        }
        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

        withScriptStructureView(file) { root, _ ->
            val configuration = findChild(root, "Script Configuration")
            assertEquals(listOf("Script Definition", "Script Configuration", "File Structure"), childTexts(root))
            assertEquals(listOf("JDK", "Default Imports"), childTexts(configuration))
            assertEquals(listOf("null"), childTexts(findChild(configuration, "JDK")))
            assertEquals(listOf("kotlin.io.path.*"), childTexts(findChild(configuration, "Default Imports")))
        }
    }

    fun testEmptyConfigurationRootGroupIsShown() {
        val definitionJar = createEmptyJar("definition-empty-configuration-test.jar")
        registerScriptContextDefinition(definitionJar, createEmptyJar("script-empty-configuration-test.jar"))

        val file = configureScript("sample.context.kts")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
            replaceStoredScriptConfiguration(
                file = file,
                configuration = ScriptCompilationConfiguration {
                    fileExtension("context.kts")
                    kotlinScriptTemplate {
                        title = "Empty Context Script"
                    }
                },
            )
        }
        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

        withScriptStructureView(file) { root, _ ->
            val configuration = findChild(root, "Script Configuration")
            assertEquals(listOf("Script Definition", "Script Configuration", "File Structure"), childTexts(root))
            assertEquals(listOf("JDK"), childTexts(configuration))
            assertEquals(listOf("null"), childTexts(findChild(configuration, "JDK")))
        }
    }

    fun testImportedScriptsAreShownInConfiguration() {
        registerScriptContextDefinition(
            definitionJar = createEmptyJar("definition-import-test.jar"),
            scriptJar = createEmptyJar("script-import-test.jar"),
        )

        val importedDir = FileUtil.createTempDirectory("script-imported-structure-view", null, true)
        val importedIoFile = File(importedDir, "helper.imported.kts").apply {
            writeText(
                """
                fun helperFromImport() {}
                """.trimIndent()
            )
        }
        requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(importedIoFile))

        val file = configureScript("sample.context.kts")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
            replaceStoredScriptConfiguration(
                file = file,
                configuration = ScriptCompilationConfiguration {
                    fileExtension("context.kts")
                    kotlinScriptTemplate {
                        title = "Imported Script Context"
                    }
                    importScripts(FileScriptSource(importedIoFile))
                },
            )
        }
        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

        withScriptStructureView(file) { root, _ ->
            val configuration = findChild(root, "Script Configuration")
            assertEquals(listOf("JDK", "Imported Scripts"), childTexts(configuration))
            assertEquals(listOf("null"), childTexts(findChild(configuration, "JDK")))

            val importedScript = children(findChild(configuration, "Imported Scripts")).single()
            assertEquals("helper.imported.kts", importedScript.presentation.presentableText)
            assertSame(KotlinIcons.SCRIPT, importedScript.presentation.getIcon(false))
            assertEquals(importedIoFile.parent, importedScript.presentation.locationString)
            assertTrue(importedScript.canNavigate())
        }
    }

    @Suppress("DEPRECATION")
    private fun registerScriptContextDefinition(definitionJar: File, scriptJar: File) {
        val hostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
            configurationDependencies.append(JvmDependency(definitionJar))
        }
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            hostConfiguration,
            compilation = {
                fileExtension("context.kts")
                kotlinScriptTemplate {
                    title = "Context Test Script"
                }
                implicitReceivers(ScriptTemplateWithArgs::class)
                providedProperties(
                    "scriptName" to KotlinType(String::class),
                    "value" to KotlinType(Int::class),
                )
                defaultImports("java.io.File", "kotlin.collections.*")
                dependencies.append(JvmDependency(scriptJar))
            },
        )

        val definition = ScriptDefinition.FromConfigurations(
            hostConfiguration,
            compilationConfiguration,
            evaluationConfiguration,
        )

        project.registerExtension(
            SCRIPT_DEFINITIONS_SOURCES,
            object : ScriptDefinitionsSource {
                override val definitions: Sequence<ScriptDefinition> = sequenceOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    private fun configureScript(fileName: String): PsiFile {
        lateinit var file: PsiFile
        runInEdtAndWait {
            file = myFixture.configureByText(
                fileName,
                """
                fun userFunction() {}
                """.trimIndent(),
            )
            myFixture.openFileInEditor(file.virtualFile)
        }
        return file
    }

    private fun loadScript(file: PsiFile) {
        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
        }
    }

    private fun createEmptyJar(name: String): File {
        val tempDir = FileUtil.createTempDirectory("script-context-test", null, true)
        val jarFile = File(tempDir, name)
        JarOutputStream(jarFile.outputStream().buffered()).use { }
        return jarFile
    }

    private suspend fun replaceStoredScriptConfiguration(file: PsiFile, configuration: ScriptCompilationConfiguration) {
        val scriptEntity = requireNotNull(KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile)) {
            "KotlinScriptEntity must exist after load"
        }
        project.workspaceModel.update("replace test script configuration") { storage ->
            val configurationId = configuration.getOrCreateScriptConfigurationId(storage, KotlinScriptEntitySource)
            storage.modifyKotlinScriptEntity(scriptEntity) {
                this.configurationId = configurationId
            }
        }
    }

    private fun withScriptStructureView(file: PsiFile, assertions: (TreeElement, Boolean) -> Unit) {
        var root: TreeElement? = null
        var isRootVisible = true
        runInEdtAndWait {
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            myFixture.testStructureView { structureView ->
                root = structureView.treeModel.root
                isRootVisible = structureView.tree.isRootVisible
            }
        }
        assertions(checkNotNull(root), isRootVisible)
    }
}

private fun children(element: TreeElement): List<StructureViewTreeElement> =
    element.children.filterIsInstance<StructureViewTreeElement>()

private fun childTexts(element: TreeElement): List<String> =
    children(element).map { requireNotNull(it.presentation.presentableText) }

private fun childTextsInReadAction(element: TreeElement): List<String> =
    ReadAction.compute<List<String>, RuntimeException> { childTexts(element) }

private fun findChild(element: TreeElement, text: String): StructureViewTreeElement =
    children(element).single { it.presentation.presentableText == text }

private fun scriptDefinitionId(file: PsiFile): String {
    val definition = requireNotNull(file.findScriptDefinition()) { "Expected Kotlin script definition for ${file.name}" }
    return definition.definitionId
}

private fun presentableLocation(project: Project, file: File): String {
    val path = file.path
    val projectBasePath = project.basePath
    return if (projectBasePath != null && FileUtil.startsWith(path, projectBasePath)) {
        "..." + path.substring(projectBasePath.length)
    } else {
        FileUtil.getLocationRelativeToUserHome(path, false)
    }
}

private fun classNodeIcon(): Icon =
    Class.forName("com.intellij.icons.AllIcons" + '$' + "Nodes").getField("Class").get(null) as Icon
