// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.shared.definition.javaHomePath
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.shared.definition.jdkSupplier
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File
import kotlin.io.path.createTempDirectory
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
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class KotlinScriptJdkFallbackTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

    private var jdksBeforeTest: Set<Sdk> = emptySet()

    override fun setUp() {
        super.setUp()
        jdksBeforeTest = ProjectJdkTable.getInstance().allJdks.toSet()
    }

    override fun tearDown() {
        try {
            removeJdksAddedByTest()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun `test load gives the script a jdk when the project has none`() {
        val jdk = registerJdk()
        assertNull("the fixture must start without a project JDK and without a module JDK", project.javaHomePath)

        val file = myFixture.configureByText("test.kts", "val f = java.io.File(\"x\")")
        runBlocking { KotlinScriptService.getInstance(project).load(file.virtualFile) }

        val entity = requireNotNull(KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile)) {
            "load must create a KotlinScriptEntity"
        }
        val sdkId = requireNotNull(entity.sdkId) { "the script entity must carry an SDK" }
        assertEquals("the script must take the JDK of the SDK table", jdk.name, sdkId.name)
        assertNotNull(
            "the SDK must resolve in the workspace model, because that is how the script module reads it",
            project.workspaceModel.currentSnapshot.resolve(sdkId)
        )
        assertNull("the project SDK must stay unset", ProjectRootManager.getInstance(project).projectSdk)
    }

    fun `test java symbols resolve in a script when the project has no jdk`() {
        registerJdk()

        val file = myFixture.configureByText("resolve.kts", "val f = java.io.File(\"x\").readText()")
        runBlocking { KotlinScriptService.getInstance(project).load(file.virtualFile) }

        val errors = runInEdtAndGet { myFixture.doHighlighting(HighlightSeverity.ERROR) }
        assertEmpty("java symbols must resolve once the script has a JDK", errors)
    }

    fun `test load adds no jdk to the sdk table in a test`() {
        val before = ProjectJdkTable.getInstance().allJdks.toSet()

        val file = myFixture.configureByText("noleak.kts", "val x = 1")
        runBlocking { KotlinScriptService.getInstance(project).load(file.virtualFile) }

        assertEquals(
            "script loading must add no JDK, because SdkLeakTracker fails every test that leaves one behind",
            before,
            ProjectJdkTable.getInstance().allJdks.toSet(),
        )
    }

    fun `test load adds no jdk to the sdk table when a definition supplies one`() {
        val unknownJdkHome = createTempDirectory("not-a-registered-jdk").toFile()
        registerJdkSupplierDefinition(unknownJdkHome)
        val before = ProjectJdkTable.getInstance().allJdks.toSet()

        val file = myFixture.configureByText("supplied.custom.kts", "val x = 1")
        runBlocking { KotlinScriptService.getInstance(project).load(file.virtualFile) }

        assertEquals(
            "a JDK home that ide.jdkSupplier gives must add no table entry either",
            before,
            ProjectJdkTable.getInstance().allJdks.toSet(),
        )
    }

    /** Registers a definition for `*.custom.kts` whose `jdkSupplier` gives a JDK home outside the SDK table. */
    private fun registerJdkSupplierDefinition(jdkHome: File) {
        val definition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("custom.kts")
                ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
                ide.jdkSupplier { jdkHome }
            },
        )
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "KotlinScriptJdkFallbackTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    /** Registers a JDK, but leaves the project without one. [tearDown] removes it again. */
    private fun registerJdk(): Sdk {
        val javaHome = requireNotNull(defaultJavaHome) { "the test JVM must expose JAVA_HOME or java.home" }
        val javaSdk = JavaSdk.getInstance()
        val jdk = javaSdk.createJdk(javaSdk.suggestSdkName(null, javaHome), javaHome, false)
        WriteAction.runAndWait<Throwable> { ProjectJdkTable.getInstance().addJdk(jdk) }
        return jdk
    }

    private fun removeJdksAddedByTest() {
        val table = ProjectJdkTable.getInstance()
        val added = table.allJdks.filterNot { it in jdksBeforeTest }
        if (added.isEmpty()) return
        WriteAction.runAndWait<Throwable> { added.forEach(table::removeJdk) }
    }
}
