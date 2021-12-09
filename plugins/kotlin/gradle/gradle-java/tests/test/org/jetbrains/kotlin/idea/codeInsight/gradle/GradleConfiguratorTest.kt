// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinJsGradleModuleConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Ignore
import org.junit.Test

abstract class GradleConfiguratorTest : KotlinGradleImportingTestCase() {
    class ProjectWithModule8 : GradleConfiguratorTest() {
        @Test
        fun testProjectWithModule() {
            importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    // Create not configured build.gradle for project
                    myProject.guessProjectDir()!!.createChildData(null, "build.gradle")

                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val moduleGroup = module.toModuleGroup()
                    // We have a Kotlin runtime in build.gradle but not in the classpath, so it doesn't make sense
                    // to suggest configuring it
                    assertEquals(ConfigureKotlinStatus.BROKEN, findGradleModuleConfigurator().getStatus(moduleGroup))
                    // Don't offer the JS configurator if the JVM configuration exists but is broken
                    assertEquals(ConfigureKotlinStatus.BROKEN, findJsGradleModuleConfigurator().getStatus(moduleGroup))
                }
            }

            assertEquals(
                """
            <p>The compiler bundled to Kotlin plugin (1.0.0) is older than external compiler used for building modules:</p>
            <ul>
            <li>project.app (${LATEST_STABLE_GRADLE_PLUGIN_VERSION})</li>
            </ul>
            <p>This may cause different set of errors and warnings reported in IDE.</p>
            <p><a href="update">Update</a>  <a href="ignore">Ignore</a></p>
            """.trimIndent().lines().joinToString(separator = ""),
                createOutdatedBundledCompilerMessage(myProject, "1.0.0")
            )
        }
    }

    class Configure10 : GradleConfiguratorTest() {
        @Test
        fun testConfigure10() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.0.6", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureKotlinWithPluginsBlock : GradleConfiguratorTest() {
        @Test
        fun testConfigureKotlinWithPluginsBlock() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.0.6", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureKotlinDevVersion : GradleConfiguratorTest() {
        @Test
        fun testConfigureKotlinDevVersion() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.60-dev-286", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureGradleKtsKotlinDevVersion : GradleConfiguratorTest() {
        @Test
        fun testConfigureGradleKtsKotlinDevVersion() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.60-dev-286", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJvmWithBuildGradle : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJvmWithBuildGradle() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJvmWithBuildGradleKts : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJvmWithBuildGradleKts() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJvmEAPWithBuildGradle : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJvmEAPWithBuildGradle() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40-eap-62", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJvmEAPWithBuildGradleKts : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJvmEAPWithBuildGradleKts() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40-eap-62", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJsWithBuildGradle : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJsWithBuildGradle() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findJsGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJsWithBuildGradleKts : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJsWithBuildGradleKts() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findJsGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJsEAPWithBuildGradle : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJsEAPWithBuildGradle() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findJsGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40-eap-62", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ConfigureJsEAPWithBuildGradleKts : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.4+")
        fun testConfigureJsEAPWithBuildGradleKts() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findJsGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.2.40-eap-62", collector)

                    checkFiles(files)
                }
            }
        }
    }

    protected fun findGradleModuleConfigurator(): KotlinGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinGradleModuleConfigurator::class.java)
    }

    protected fun findJsGradleModuleConfigurator(): KotlinJsGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinJsGradleModuleConfigurator::class.java)
    }

    class ConfigureGSK : GradleConfiguratorTest() {
        @Test
        fun testConfigureGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                    val configurator = findGradleModuleConfigurator()
                    val collector = createConfigureKotlinNotificationCollector(myProject)
                    configurator.configureWithVersion(myProject, listOf(module), "1.1.2", collector)

                    checkFiles(files)
                }
            }
        }
    }

    class ListNonConfiguredModules : GradleConfiguratorTest() {
        @Test
        fun testListNonConfiguredModules() {
            importProjectFromTestData()

            runReadAction {
                val configurator = findGradleModuleConfigurator()

                val (modules, ableToRunConfigurators) = getConfigurationPossibilitiesForConfigureNotification(myProject)
                assertTrue(ableToRunConfigurators.any { it is KotlinGradleModuleConfigurator })
                assertTrue(ableToRunConfigurators.any { it is KotlinJsGradleModuleConfigurator })
                val moduleNames = modules.map { it.baseModule.name }
                assertSameElements(moduleNames, "project.app")

                val moduleNamesFromConfigurator = getCanBeConfiguredModules(myProject, configurator).map { it.name }
                assertSameElements(moduleNamesFromConfigurator, "project.app")

                val moduleNamesWithKotlinFiles = getCanBeConfiguredModulesWithKotlinFiles(myProject, configurator).map { it.name }
                assertSameElements(moduleNamesWithKotlinFiles, "project.app")
            }
        }
    }

    class ListNonConfiguredModulesConfigured : GradleConfiguratorTest() {
        @Test
        fun testListNonConfiguredModulesConfigured() {
            importProjectFromTestData()

            runReadAction {
                assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
            }
        }
    }

    class ListNonConfiguredModulesConfiguredWithImplementation : GradleConfiguratorTest() {
        @Test
        fun testListNonConfiguredModulesConfiguredWithImplementation() {
            importProjectFromTestData()

            runReadAction {
                assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
            }
        }
    }

    class ListNonConfiguredModulesConfiguredOnlyTest : GradleConfiguratorTest() {
        @Test
        fun testListNonConfiguredModulesConfiguredOnlyTest() {
            importProjectFromTestData()

            runReadAction {
                assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
            }
        }
    }

    class TestTasksAreImported : GradleConfiguratorTest() {
        @Ignore
        @Test
        fun testTestTasksAreImported() {
            importProjectFromTestData()

            @Suppress("DEPRECATION")
            val testTasks = getTasksToRun(myTestFixture.module)

            assertTrue("There should be at least one test task", testTasks.isNotEmpty())
        }
    }

    @Deprecated("restored from org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer#getTasksToRun")
    fun getTasksToRun(module: Module): List<String> {
        for (provider in GradleTestTasksProvider.EP_NAME.extensions) {
            val tasks = provider.getTasks(module)
            if (!ContainerUtil.isEmpty(tasks)) {
                return tasks
            }
        }
        val externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module)
            ?: return ContainerUtil.emptyList()
        val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
            ?: return ContainerUtil.emptyList()
        val externalProjectInfo = ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, projectPath)
            ?: return ContainerUtil.emptyList()
        val tasks: List<String>
        val gradlePath = GradleProjectResolverUtil.getGradlePath(module)
            ?: return ContainerUtil.emptyList()
        val taskPrefix = if (StringUtil.endsWithChar(gradlePath, ':')) gradlePath else "$gradlePath:"
        val moduleNode = GradleProjectResolverUtil.findModule(externalProjectInfo.externalProjectStructure, projectPath)
            ?: return ContainerUtil.emptyList()
        val taskNode: DataNode<TaskData>?
        val sourceSetId = StringUtil.substringAfter(externalProjectId, moduleNode.data.externalName + ':')
        taskNode = if (sourceSetId == null) {
            ExternalSystemApiUtil.find(
                moduleNode, ProjectKeys.TASK
            ) { node: DataNode<TaskData> ->
                node.data.isTest &&
                        StringUtil.equals(
                            "test",
                            node.data.name
                        ) || StringUtil.equals(taskPrefix + "test", node.data.name)
            }
        } else {
            ExternalSystemApiUtil.find(
                moduleNode, ProjectKeys.TASK
            ) { node: DataNode<TaskData> ->
                node.data.isTest && StringUtil.startsWith(node.data.name, sourceSetId)
            }
        }
        if (taskNode == null) return ContainerUtil.emptyList()
        val taskName = StringUtil.trimStart(taskNode.data.name, taskPrefix)
        tasks = listOf(taskName)
        return ContainerUtil.map(
            tasks
        ) { task: String -> taskPrefix + task }
    }

    class AddNonKotlinLibraryGSK : GradleConfiguratorTest() {
        @Test
        fun testAddNonKotlinLibraryGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                        myTestFixture.module,
                        DependencyScope.COMPILE,
                        object : ExternalLibraryDescriptor("org.a.b", "lib", "1.0.0", "1.0.0") {
                            override fun getLibraryClassesRoots() = emptyList<String>()
                        })
                }

                checkFiles(files)
            }
        }
    }

    class AddTestLibraryGSK07 : GradleConfiguratorTest() {
        @Test
        fun testAddTestLibraryGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                        myTestFixture.module,
                        DependencyScope.TEST,
                        object : ExternalLibraryDescriptor("junit", "junit", "4.12", "4.12") {
                            override fun getLibraryClassesRoots() = emptyList<String>()
                        })

                    KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                        myTestFixture.module,
                        DependencyScope.TEST,
                        object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-test", "1.1.2", "1.1.2") {
                            override fun getLibraryClassesRoots() = emptyList<String>()
                        })
                }

                checkFiles(files)
            }
        }
    }

    class AddLibraryGSK : GradleConfiguratorTest() {
        @Test
        fun testAddLibraryGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                        myTestFixture.module,
                        DependencyScope.COMPILE,
                        object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.0.0", "1.0.0") {
                            override fun getLibraryClassesRoots() = emptyList<String>()
                        })
                }

                checkFiles(files)
            }
        }
    }

    class AddLanguageVersion : GradleConfiguratorTest() {
        @Test
        fun testAddLanguageVersion() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
                }

                checkFiles(files)
            }
        }
    }

    class AddLanguageVersionGSK : GradleConfiguratorTest() {
        @Test
        fun testAddLanguageVersionGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
                }

                checkFiles(files)
            }
        }
    }

    class ChangeLanguageVersion : GradleConfiguratorTest() {
        @Test
        fun testChangeLanguageVersion() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
                }

                checkFiles(files)
            }
        }
    }

    class ChangeLanguageVersionGSK : GradleConfiguratorTest() {
        @Test
        fun testChangeLanguageVersionGSK() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
                }

                checkFiles(files)
            }
        }
    }

    class AddLibrary : GradleConfiguratorTest() {
        @Test
        fun testAddLibrary() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                myTestFixture.project.executeWriteCommand("") {
                    KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                        myTestFixture.module,
                        DependencyScope.COMPILE,
                        object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.0.0", "1.0.0") {
                            override fun getLibraryClassesRoots() = emptyList<String>()
                        })
                }

                checkFiles(files)
            }
        }
    }

    protected fun doTestChangeFeatureSupport() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class ChangeFeatureSupport : GradleConfiguratorTest() {
        @Test
        fun testChangeFeatureSupport() = doTestChangeFeatureSupport()
    }

    class ChangeFeatureSupportWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testChangeFeatureSupportWithXFlag() = doTestChangeFeatureSupport()
    }

    protected fun doTestDisableFeatureSupport() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.DISABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class DisableFeatureSupport : GradleConfiguratorTest() {
        @Test
        fun testDisableFeatureSupport() = doTestDisableFeatureSupport()
    }

    class DisableFeatureSupportWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testDisableFeatureSupportWithXFlag() = doTestDisableFeatureSupport()
    }

    protected fun doTestEnableFeatureSupport() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class EnableFeatureSupport : GradleConfiguratorTest() {
        @Test
        fun testEnableFeatureSupport() = doTestEnableFeatureSupport()
    }

    class EnableFeatureSupportWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        @JvmName("testEnableFeatureSupportWithXFlag")
        fun testEnableFeatureSupportWithXFlag() = doTestEnableFeatureSupport()
    }

    class EnableFeatureSupportToExistentArguments : GradleConfiguratorTest() {
        @Test
        fun testEnableFeatureSupportToExistentArguments() {
            val files = importProjectFromTestData()

            runInEdtAndWait {
                runWriteAction {
                    KotlinWithGradleConfigurator.changeFeatureConfiguration(
                        myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                    )
                }

                checkFiles(files)
            }
        }
    }

    protected fun doTestEnableFeatureSupportGSKWithoutFoundKotlinVersion() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class EnableFeatureSupportGSKWithoutFoundKotlinVersion : GradleConfiguratorTest() {
        @Test
        fun testEnableFeatureSupportGSKWithoutFoundKotlinVersion() = doTestEnableFeatureSupportGSKWithoutFoundKotlinVersion()
    }

    class EnableFeatureSupportToExistentArgumentsWithXFlag : GradleConfiguratorTest() {
        @TargetVersions("4.7+")
        @Test
        fun testEnableFeatureSupportToExistentArgumentsWithXFlag() = doTestEnableFeatureSupportGSKWithoutFoundKotlinVersion()
    }

    protected fun doTestChangeFeatureSupportGSK() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.DISABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class ChangeFeatureSupportGSK : GradleConfiguratorTest() {
        @Test
        fun testChangeFeatureSupportGSK() = doTestChangeFeatureSupportGSK()
    }

    class ChangeFeatureSupportGSKWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testChangeFeatureSupportGSKWithXFlag() = doTestChangeFeatureSupportGSK()
    }

    protected fun doTestDisableFeatureSupportGSK() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.DISABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class DisableFeatureSupportGSK : GradleConfiguratorTest() {
        @Test
        fun testDisableFeatureSupportGSK() = doTestDisableFeatureSupportGSK()
    }

    class DisableFeatureSupportGSKWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testDisableFeatureSupportGSKWithXFlag() = doTestDisableFeatureSupportGSK()
    }

    protected fun doTestEnableFeatureSupportGSK() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeFeatureConfiguration(
                    myTestFixture.module, LanguageFeature.InlineClasses, LanguageFeature.State.ENABLED, false
                )
            }

            checkFiles(files)
        }
    }

    class EnableFeatureSupportGSK : GradleConfiguratorTest() {
        @Test
        fun testEnableFeatureSupportGSK() = doTestEnableFeatureSupportGSK()
    }

    class EnableFeatureSupportGSKWithXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testEnableFeatureSupportGSKWithXFlag() = doTestEnableFeatureSupportGSK()
    }

    class EnableFeatureSupportGSKWithNotInfixVersionCallAndXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testEnableFeatureSupportGSKWithNotInfixVersionCallAndXFlag() = doTestEnableFeatureSupportGSK()
    }

    class EnableFeatureSupportGSKWithSpecifyingPluginThroughIdAndXFlag : GradleConfiguratorTest() {
        @Test
        @TargetVersions("4.7+")
        fun testEnableFeatureSupportGSKWithSpecifyingPluginThroughIdAndXFlag() = doTestEnableFeatureSupportGSK()
    }

    override fun testDataDirName(): String = "configurator"
}
