// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.facet.FacetManager
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory
import org.junit.*
import java.nio.file.Files

class KotlinFacetSerializationTest {
    companion object {
        @JvmField
        @ClassRule
        val appRule = ApplicationRule()
    }

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val tempDirManager = TemporaryDirectory()

    lateinit var module: Module

    @Before
    fun verifyImlDoesntExist() {
        //TODO: remove after enabling by default
        Registry.get("workspace.model.kotlin.facet.bridge").setValue(true)
        Assume.assumeTrue("Execute only if kotlin facet bridge enabled", KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled)
        TestCase.assertFalse(Files.exists(projectRule.module.moduleNioFile))
    }

    @Test
    fun `test simple kotlin settings serialization old implementation`() = runBlocking {
        executeWithKotlinFacetBridgeAsync(false) {
            checkSimpleKotlinSettingsSerialization()
        }
    }

    @Test
    fun `test simple kotlin settings serialization new implementation`() = runBlocking {
        executeWithKotlinFacetBridgeAsync(true) {
            checkSimpleKotlinSettingsSerialization()
        }
    }

    @Test
    fun `test complex kotlin settings serialization old implementation`() = runBlocking {
        executeWithKotlinFacetBridgeAsync(false) {
            checkComplexKotlinSettingsSerialization()
        }
    }

    @Test
    fun `test complex kotlin settings serialization new implementation`() = runBlocking {
        Assume.assumeTrue("Execute only if kotlin facet bridge enabled", KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled)
        executeWithKotlinFacetBridgeAsync(true) {
            checkComplexKotlinSettingsSerialization()
        }
    }

    private suspend fun checkComplexKotlinSettingsSerialization() {
        val project = projectRule.project
        val module = projectRule.module

        withContext(Dispatchers.EDT) {
            val mainFacet = FacetUtil.addFacet(module, KotlinFacetType.INSTANCE)

            with(mainFacet.configuration.settings) {
                useProjectSettings = false
                isHmppEnabled = true
                kind = KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER
                compilerArguments = K2JVMCompilerArguments()
                apiLevel = LanguageVersion.KOTLIN_1_0
                languageLevel = LanguageVersion.KOTLIN_1_3
                implementedModuleNames = listOf("implementedModule1", "implementedModule2")
                dependsOnModuleNames = listOf("dependsOnModule1", "dependsOnModule2")
                additionalVisibleModuleNames = setOf("friend1", "friend2")
            }


            fireFacetChanged(mainFacet)

        }
        project.stateStore.save()

        val content: String = Files.readString(module.moduleNioFile).replace("\r\n", "\n")
        TestCase.assertEquals(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <module type="EMPTY_MODULE" version="4">
              <component name="FacetManager">
                <facet type="kotlin-language" name="Kotlin">
                  <configuration version="5" useProjectSettings="false" isTestModule="false" isHmppProject="true">
                    <implements>
                      <implement>implementedModule1</implement>
                      <implement>implementedModule2</implement>
                    </implements>
                    <dependsOnModuleNames>
                      <dependsOn>dependsOnModule1</dependsOn>
                      <dependsOn>dependsOnModule2</dependsOn>
                    </dependsOnModuleNames>
                    <additionalVisibleModuleNames>
                      <friend>friend1</friend>
                      <friend>friend2</friend>
                    </additionalVisibleModuleNames>
                    <newMppModelJpsModuleKind>COMPILATION_AND_SOURCE_SET_HOLDER</newMppModelJpsModuleKind>
                    <compilerArguments>
                      <stringArguments>
                        <stringArg name="apiVersion" arg="1.0" />
                        <stringArg name="languageVersion" arg="1.3" />
                      </stringArguments>
                    </compilerArguments>
                  </configuration>
                </facet>
              </component>
              <component name="NewModuleRootManager" inherit-compiler-output="true">
                <exclude-output />
                <content url="temp:///src">
                  <sourceFolder url="temp:///src" isTestSource="false" />
                </content>
                <orderEntry type="sourceFolder" forTests="false" />
              </component>
            </module>
            """.trimIndent(), content
        )

        withContext(Dispatchers.EDT) {
            runWriteActionAndWait {
                val facetManager = FacetManager.getInstance(module)
                val model = facetManager.createModifiableModel()
                model.removeFacet(facetManager.allFacets[0])
                model.commit()
            }
        }
    }


    private suspend fun executeWithKotlinFacetBridgeAsync(facetBridgeEnabled: Boolean, action: suspend () -> Unit) {
        val registryValue = Registry.get("workspace.model.kotlin.facet.bridge")
        val initValue = registryValue.asBoolean()
        try {
            registryValue.setValue(facetBridgeEnabled)
            return action()
        } finally {
            registryValue.setValue(initValue)
        }
    }

    private suspend fun checkSimpleKotlinSettingsSerialization() {
        val project = projectRule.project
        val module = projectRule.module

        withContext(Dispatchers.EDT) {
            FacetUtil.addFacet(module, KotlinFacetType.INSTANCE)
        }
        project.stateStore.save()

        TestCase.assertTrue(Files.exists(module.moduleNioFile))
        val content: String = Files.readString(module.moduleNioFile).replace("\r\n", "\n")
        TestCase.assertEquals(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <module type="EMPTY_MODULE" version="4">
              <component name="FacetManager">
                <facet type="kotlin-language" name="Kotlin">
                  <configuration version="5" />
                </facet>
              </component>
              <component name="NewModuleRootManager" inherit-compiler-output="true">
                <exclude-output />
                <content url="temp:///src">
                  <sourceFolder url="temp:///src" isTestSource="false" />
                </content>
                <orderEntry type="sourceFolder" forTests="false" />
              </component>
            </module>
            """.trimIndent(), content
        )

        withContext(Dispatchers.EDT) {
            runWriteActionAndWait {
                val facetManager = FacetManager.getInstance(module)
                val model = facetManager.createModifiableModel()
                model.removeFacet(facetManager.allFacets[0])
                model.commit()
            }
        }
    }

    private fun fireFacetChanged(facet: KotlinFacet) {
        val module: Module = facet.module
        if (!module.isDisposed && !module.project.isDisposed) {
            FacetManager.getInstance(module).facetConfigurationChanged(facet)
        }
    }
}