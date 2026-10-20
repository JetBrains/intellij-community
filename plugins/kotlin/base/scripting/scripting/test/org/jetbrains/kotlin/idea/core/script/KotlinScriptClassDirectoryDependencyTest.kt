// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.registerExtension
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.Decompressor
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.templates.standard.ScriptTemplateWithArgs

private val HELPER_SOURCE = """
    package dep

    class Helper {
        fun greet(): String = "hi"
        fun log(value: String) {}
        fun log(value: Int) {}
    }

    fun helperTop(): Int = 1
    fun String.pretty(): String = this
    fun Int.pretty(): String = toString()
""".trimIndent()

private val NAVIGATION_SCRIPT = """
    import dep.pretty

    dep.Helper().greet()
    dep.Helper().log(1)
    dep.helperTop()
    1.pretty()
""".trimIndent()

/** Checks that a script resolves a class from a class directory on the classpath of its definition. */
class KotlinScriptClassDirectoryDependencyTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun runInDispatchThread(): Boolean = false

    // KTIJ-40452: Gradle sync makes `build/classes/kotlin/main` the compiler output of the module
    fun testClassDirectoryIsCompilerOutputOfModule() {
        val classes = compileHelperTo(FileUtil.createTempDirectory("output", null, true))
        runInEdtAndWait { PsiTestUtil.setCompilerOutputPath(module, refreshAndFind(classes).url, false) }
        try {
            checkHelperResolves(classes)
        }
        finally {
            runInEdtAndWait {
                ModuleRootModificationUtil.updateModel(module) {
                    it.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(true)
                }
            }
        }
    }

    fun testNavigationFromModuleOutputLeadsToModuleSource() {
        withScriptOnModuleOutput("navigation.probe.kts") { script, helperSource ->
            runReadActionBlocking {
                assertNavigationLeadsToSource(resolveScriptTargets(script), helperSource)
                val helperFile = PsiManager.getInstance(project).findFile(helperSource)!!
                assertInstanceOf(helperFile.getKaModule(project, useSiteModule = null), KaSourceModule::class.java)
            }
        }
    }

    fun testNavigationFromModuleOutputInDumbMode() {
        withScriptOnModuleOutput("dumbNavigation.probe.kts") { script, helperSource ->
            // The IDE resolves in the smart mode. Only the navigation runs in the dumb mode.
            val targets = runReadActionBlocking { resolveScriptTargets(script) }
            DumbModeTestUtils.runInDumbModeSynchronously(project) {
                runReadActionBlocking { assertNavigationLeadsToSource(targets, helperSource) }
            }
        }
    }

    fun testRegularLibraryOnModuleOutputKeepsStandardNavigation() {
        val classes = compileHelperTo(FileUtil.createTempDirectory("output", null, true))
        val classesUrl = refreshAndFind(classes).url
        runInEdtAndWait {
            PsiTestUtil.setCompilerOutputPath(module, classesUrl, false)
            PsiTestUtil.addLibrary(module, classes.path)
            myFixture.addFileToProject("dep/Helper.kt", HELPER_SOURCE)
        }
        try {
            runReadActionBlocking {
                val helperClass = refreshAndFind(File(classes, "dep/Helper.class"))
                val compiledHelper = PsiTreeUtil.findChildOfType(PsiManager.getInstance(project).findFile(helperClass), PsiNameIdentifierOwner::class.java)!!
                assertEquals("Only a script library gets the module source", helperClass, compiledHelper.navigationElement.containingFile.virtualFile)
            }
        }
        finally {
            runInEdtAndWait {
                ModuleRootModificationUtil.updateModel(module) { model ->
                    model.orderEntries.filterIsInstance<LibraryOrderEntry>()
                        .filter { classesUrl in it.getRootUrls(OrderRootType.CLASSES) }
                        .forEach(model::removeOrderEntry)
                    model.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(true)
                }
            }
        }
    }

    /** The light project keeps the script entity of an earlier test, so each test needs a script of its own. */
    private fun withScriptOnModuleOutput(scriptName: String, check: (script: VirtualFile, helperSource: VirtualFile) -> Unit) {
        val classes = compileHelperTo(FileUtil.createTempDirectory("output", null, true))
        val helperSource = runInEdtAndGet {
            PsiTestUtil.setCompilerOutputPath(module, refreshAndFind(classes).url, false)
            myFixture.addFileToProject("dep/Helper.kt", HELPER_SOURCE).virtualFile
        }
        try {
            registerDefinition(classes)
            val script = myFixture.tempDirFixture.createFile("scripts/$scriptName", NAVIGATION_SCRIPT)
            runBlocking {
                KotlinScriptService.getInstance(project).load(script)
            }
            runInEdtAndWait { myFixture.configureFromExistingVirtualFile(script) }
            check(script, helperSource)
        }
        finally {
            runInEdtAndWait {
                ModuleRootModificationUtil.updateModel(module) {
                    it.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(true)
                }
            }
        }
    }

    private fun resolveScriptTargets(script: VirtualFile): Map<String, PsiElement?> {
        val scriptFile = PsiManager.getInstance(project).findFile(script)!!
        return listOf("Helper", "greet", "helperTop", "log(1)", "pretty()").associateWith {
            scriptFile.findReferenceAt(scriptFile.text.indexOf(it))?.resolve()
        }
    }

    private fun assertNavigationLeadsToSource(targets: Map<String, PsiElement?>, helperSource: VirtualFile) {
        fun navigationAt(text: String): KtNamedDeclaration? = targets.getValue(text)?.navigationElement as? KtNamedDeclaration
        for (name in listOf("Helper", "greet", "helperTop")) {
            val navigationElement = navigationAt(name)
            assertEquals("Navigation of $name must lead to the module source", helperSource, navigationElement?.containingFile?.virtualFile)
            assertEquals(name, navigationElement?.name)
        }
        // The overloads and the extensions with the String variant come first, so a match by name alone takes the wrong one.
        val log = navigationAt("log(1)")
        assertEquals(helperSource, log?.containingFile?.virtualFile)
        assertEquals("fun log(value: Int) {}", log?.text)
        val pretty = navigationAt("pretty()")
        assertEquals(helperSource, pretty?.containingFile?.virtualFile)
        assertEquals("fun Int.pretty(): String = toString()", pretty?.text)
    }

    fun testClassDirectoryOutsideProject() {
        checkHelperResolves(compileHelperTo(FileUtil.createTempDirectory("classes", null, true)))
    }

    private fun checkHelperResolves(classes: File) {
        registerDefinition(classes)
        val script = myFixture.tempDirFixture.createFile("scripts/script.probe.kts", "dep.Helper()")

        runBlocking {
            KotlinScriptService.getInstance(project).load(script)
        }

        runInEdtAndWait {
            myFixture.configureFromExistingVirtualFile(script)
            assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR).map { it.description })
        }
    }

    private fun compileHelperTo(target: File): File {
        val sources = FileUtil.createTempDirectory("sources", null, true)
        File(sources, "Helper.kt").writeText(HELPER_SOURCE)
        Decompressor.Zip(KotlinCompilerStandalone(listOf(sources)).compile().toPath()).extract(target.toPath())
        refreshAndFind(target)
        return target
    }

    private fun refreshAndFind(file: File): VirtualFile =
        requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)) { "No virtual file for $file" }

    private fun registerDefinition(classes: File) {
        val definition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("probe.kts")
                ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
                updateClasspath(listOf(classes, TestKotlinArtifacts.kotlinStdlib.toFile(), TestKotlinArtifacts.kotlinScriptRuntime.toFile()))
            },
        )
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "KotlinScriptClassDirectoryDependencyTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
