// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.configuration.notifications.LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME
import org.jetbrains.kotlin.idea.configuration.notifications.dropHotfixPart
import org.jetbrains.kotlin.idea.configuration.notifications.showNewKotlinCompilerAvailableNotificationIfNeeded
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

class GradleConfiguratorTest : KotlinGradleImportingTestCase() {
    private lateinit var foojayPropertyMap: Map<String, String>
    override fun setUp() {
        super.setUp()
        foojayPropertyMap = mapOf("FOOJAY_VERSION" to Versions.GRADLE_PLUGINS.FOOJAY_VERSION.text)
    }

    @Test
    fun testProjectWithModule() {
        val propertyKey = LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME
        val propertiesComponent = PropertiesComponent.getInstance()

        val kotlinVersion = KotlinPluginLayout.standaloneCompilerVersion
        val notificationText = KotlinMigrationBundle.message(
            "kotlin.external.compiler.updates.notification.content.0",
            kotlinVersion.kotlinVersion,
        )

        val counter = AtomicInteger(0)
        val myDisposable = Disposer.newDisposable()
        try {
            val connection = myProject.messageBus.connect(myDisposable)
            connection.subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    if (notificationText == notification.content) {
                        counter.incrementAndGet()
                    }
                }
            })

            propertiesComponent.unsetValue(propertyKey)
            assertFalse(propertiesComponent.isValueSet(propertyKey))

            connection.deliverImmediately()
            assertEquals(0, counter.get())

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
                }
            }

            val externalCompilerVersion = ExternalCompilerVersionProvider.findLatest(myProject)
            assertEquals(
                IdeKotlinVersion.get(LATEST_STABLE_GRADLE_PLUGIN_VERSION),
                externalCompilerVersion
            )

            // Should show notification if the bundled version > external compiler version
            val shouldShowNotification = kotlinVersion.isRelease && kotlinVersion.kotlinVersion.dropHotfixPart > externalCompilerVersion!!.kotlinVersion
            val expectedCountAfter = if (shouldShowNotification) 1 else 0
            runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
            connection.deliverImmediately() // the first notification from import action
            assertEquals(expectedCountAfter, counter.get())

            showNewKotlinCompilerAvailableNotificationIfNeeded(myProject)

            runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
            connection.deliverImmediately()

            showNewKotlinCompilerAvailableNotificationIfNeeded(myProject)
            showNewKotlinCompilerAvailableNotificationIfNeeded(myProject)

            runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
            connection.deliverImmediately()
            assertEquals(expectedCountAfter, counter.get())

            if (shouldShowNotification) {
                assertTrue(propertiesComponent.isValueSet(propertyKey))
            } else {
                assertFalse(propertiesComponent.isValueSet(propertyKey))
            }
        } finally {
            propertiesComponent.unsetValue(propertyKey)
            Disposer.dispose(myDisposable)
        }
    }

    @Test
    @TargetVersions("6.0+")
    fun testProjectWithSubmodule() {
        importProjectFromTestData()
        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.submodule.main")!!
                val moduleGroup = module.toModuleGroup()
                assertEquals(ConfigureKotlinStatus.CAN_BE_CONFIGURED, findGradleModuleConfigurator().getStatus(moduleGroup))
            }
        }
    }

    @Test
    @TargetVersions("6.0+")
    fun testProjectWithSubmoduleKts() {
        importProjectFromTestData()
        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.submodule.main")!!
                val moduleGroup = module.toModuleGroup()
                assertEquals(ConfigureKotlinStatus.CAN_BE_CONFIGURED, findGradleModuleConfigurator().getStatus(moduleGroup))
            }
        }
    }

    @Test
    fun testConfigure10() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.0.6"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureKotlinWithPluginsBlock() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.0.6"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    fun testConfigureKotlinDevVersion() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.2.60-dev-286"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    fun testConfigureGradleKtsKotlinDevVersion() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.2.60-dev-286"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureJvmWithBuildGradle() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.2.40"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureJvmWithBuildGradleKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.2.40"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("5.6.4 <=> 6.8.3")
    fun testConfigureJvmMilestoneWithBuildGradle() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.6.20-M1"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("5.6.4 <=> 6.8.3")
    fun testConfigureJvmMilestoneWithBuildGradleKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.6.20-M1"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureJvmKotlin17WithBuildGradleSourceCompat16() { // jvmTarget = 1.8 expected to be used instead of 1.6
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.7.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureJvmKotlin17WithBuildGradleTargetCompat16() { // jvmTarget = 1.8 expected to be used instead of 1.6
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.7.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureAllModulesInJvmProjectGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule, subModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureAllModulesInJvmProjectKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule, subModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureRootModuleInJvmProjectGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureRootModuleInJvmProjectKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureSubModuleInJvmProjectGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(subModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("<=6.8.3")
    fun testConfigureSubModuleInJvmProjectKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(subModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                val subModules = listOf("app")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    private fun findGradleModuleConfigurator(): KotlinGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinGradleModuleConfigurator::class.java)
    }

    @Test
    fun testConfigureGSK() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.1.2"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files)
            }
        }
    }

    private fun runJvmToolchainTest() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val module = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(module),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )

                checkFiles(files, foojayPropertyMap)
            }
        }
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainOnlySourceCompat() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainOnlySourceCompatKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainSourceCompatWithDot() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainDiffSourceAndTarget() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainDiffSourceAndTargetKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainMultipleTarget() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainOnlyTargetCompat() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6")
    fun testTargetCompatibilityDefinedViaJavaDot() {
        runJvmToolchainTest()
    }

    /* This test doesn't work because configureEach is lazy and target compatibility is calculated on the base of installed on a machine JDK
    or using Gradle JDK. May be fixed in KTIJ-25827
     */
/*    @Test
    @TargetVersions("5.6.4")
    fun testTakeTargetCompatibilityFromConfigureEachKts() {
        runJvmToolchainTest()
    }*/

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaToolchain() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaToolchainKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaDotToolchain() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaDotToolchainKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaToolchainInOneLine() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainIgnoreIfJavaToolchainInOneLineKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinJvmToolchainSetterKts() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinVersionPluginManagementGradleProperties() {
        runJvmToolchainTest()
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureKotlinVersionPluginManagementGradlePropertiesKts() {
        runJvmToolchainTest()
    }

    @Test
    fun testListNonConfiguredModules() {
        importProjectFromTestData()

        val configurator = findGradleModuleConfigurator()

        val (modules, ableToRunConfigurators) = ReadAction
            .nonBlocking(Callable { getConfigurationPossibilitiesForConfigureNotification(myProject) })
            .executeSynchronously()
        assertTrue(ableToRunConfigurators.any { it is KotlinGradleModuleConfigurator })
        val moduleNames = modules.map { it.baseModule.name }
        assertSameElements(moduleNames, "project.app")

        val moduleNamesFromConfigurator = ReadAction
            .nonBlocking(Callable { getCanBeConfiguredModules(myProject, configurator).map { it.name } })
            .executeSynchronously()
        assertSameElements(moduleNamesFromConfigurator, "project.app")

        val moduleNamesWithKotlinFiles = ReadAction
            .nonBlocking(Callable { getCanBeConfiguredModulesWithKotlinFiles(myProject, configurator).map { it.name } })
            .executeSynchronously()
        assertSameElements(moduleNamesWithKotlinFiles, "project.app")
    }

    @Test
    @TargetVersions("<7.6")
    fun testListNonConfiguredModulesConfigured() {
        importProjectFromTestData()

        val modules = ReadAction
            .nonBlocking(Callable { getConfigurationPossibilitiesForConfigureNotification(myProject).first })
            .executeSynchronously()
        assertEmpty(modules)
    }

    @Test
    fun testListNonConfiguredModulesConfiguredWithImplementation() {
        importProjectFromTestData()

        val modules = ReadAction
            .nonBlocking(Callable { getConfigurationPossibilitiesForConfigureNotification(myProject).first })
            .executeSynchronously()
        assertEmpty(modules)
    }

    @Test
    @TargetVersions("<7.6")
    fun testListNonConfiguredModulesConfiguredOnlyTest() {
        importProjectFromTestData()

        val modules = ReadAction
            .nonBlocking(Callable { getConfigurationPossibilitiesForConfigureNotification(myProject).first })
            .executeSynchronously()
        assertEmpty(modules)
    }

    @Ignore
    @Test
    fun testTestTasksAreImported() {
        importProjectFromTestData()

        @Suppress("DEPRECATION")
        val testTasks = getTasksToRun(myTestFixture.module)

        assertTrue("There should be at least one test task", testTasks.isNotEmpty())
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
        val externalProjectInfo =
            ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, projectPath)
                ?: return ContainerUtil.emptyList()
        val tasks: List<String>
        val gradlePath = getGradleIdentityPathOrNull(module)
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

    @Test
    fun testAddLanguageVersionModernKotlinSyntax() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.8", null, false)
            }

            checkFiles(files)
        }
    }

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

    @Test
    fun testAddLanguageVersionGSKModernKotlinSyntax() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.8", null, false)
            }

            checkFiles(files)
        }
    }

    private fun changeLanguageVersion(languageVersion: String) {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, languageVersion, null, false)
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLanguageVersionIfKotlinOptionsDslExistsGroovy() {
        changeLanguageVersion("1.1")
    }

    @Test
    fun testAddLanguageVersionIfKotlinOptionsDslExistsKts() {
        changeLanguageVersion("1.2")
    }

    @Test
    fun testAddLanguageVersionIfCompilerOptionsDslExistsGroovy() {
        changeLanguageVersion("1.3")
    }

    @Test
    fun testAddLanguageVersionIfCompilerOptionsDslExistsKts() {
        changeLanguageVersion("1.4")
    }

    @Test
    fun testDontTouchSameLanguageVersionInCompilerOptionsGroovy() {
        changeLanguageVersion("1.7")
    }

    @Test
    fun testDontTouchSameLanguageVersionInCompilerOptionsKts() {
        changeLanguageVersion("1.7")
    }

    @Test
    fun testDontTouchSameLanguageVersionInKotlinOptionsGroovy() {
        changeLanguageVersion("1.7")
    }

    @Test
    fun testDontTouchSameLanguageVersionInKotlinOptionsKts() {
        changeLanguageVersion("1.7")
    }

    private fun addInlineClasses() {
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

    @Test
    fun testDontTouchSameFreeCompilerArgsInKotlinOptionsKts() {
        addInlineClasses()
    }

    @Test
    fun testDontTouchSameFreeCompilerArgsInKotlinOptionsGroovy() {
        addInlineClasses()
    }

    @Test
    fun testDontTouchSameFreeCompilerArgsInCompilerOptionsKts() {
        addInlineClasses()
    }

    @Test
    fun testDontTouchSameFreeCompilerArgsInCompilerOptionsGroovy() {
        addInlineClasses()
    }

    @Test
    fun testReplaceLanguageVersionInCompilerOptionsGroovy() {
        changeLanguageVersion("1.9")
    }

    @Test
    fun testReplaceLanguageVersionInCompilerOptionsKts() {
        changeLanguageVersion("2.0")
    }

    @Test
    fun testReplaceLanguageVersionInKotlinOptionsGroovy() {
        changeLanguageVersion("2.1")
    }

    @Test
    fun testReplaceLanguageVersionInKotlinOptionsKts() {
        changeLanguageVersion("2.2")
    }

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

    @Test
    @TargetVersions("8.2+") // Don't want to bring a new version of Gradle only because of this test because it will increase common test time
    fun testChangeLanguageVersionInCompilerOptionsKts() {
        changeLanguageVersion("2.3")
    }

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

    @Test
    fun testChangeFeatureSupport() {
        addInlineClasses()
    }

    // compilerOptions + same option with another value
    @Test
    fun testChangeFeatureSupportCompilerOptionsKts() {
        addInlineClasses()
    }

    @Test
    @TargetVersions("8.2+")
    fun testChangeFeatureSupportCompilerOptionsAssignmentSyntaxKts() {
        addInlineClasses()
    }

    @Test
    fun testChangeFeatureSupportCompilerOptions() {
        addInlineClasses()
    }

    @Test
    fun testChangeFeatureSupportCompilerOptionsAssignmentSyntax() {
        addInlineClasses()
    }

    @Test
    @TargetVersions("4.7+")
    fun testChangeFeatureSupportWithXFlag() = testChangeFeatureSupport()

    @Test
    fun testDisableFeatureSupport() {
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

    @Test
    @TargetVersions("4.7+")
    fun testDisableFeatureSupportWithXFlag() = testDisableFeatureSupport()

    @Test
    @TargetVersions("4.7+")
    fun testDisableFeatureSupportWithXFlagModernKotlinSyntax() = testDisableFeatureSupport()

    @Test
    fun testEnableFeatureSupport() {
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

    @Test
    @TargetVersions("4.7+")
    @JvmName("testEnableFeatureSupportWithXFlag")
    fun testEnableFeatureSupportWithXFlag() = testEnableFeatureSupport()

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

    @Test
    fun testEnableFeatureSupportToExistentArgumentsKts() {
        addInlineClasses()
    }

    @Test
    fun testEnableFeatureSupportToExistentArgumentsCompilerOptions() {
        addInlineClasses()
    }


    @Test
    fun testEnableFeatureSupportToExistentArgumentsCompilerOptionsKts() {
        addInlineClasses()
    }

    @Test
    fun testChangeLanguageVersionInCompilerOptionsGroovy() {
        changeLanguageVersion("1.6")
    }

    @Test
    fun testChangeLanguageVersionInCompilerOptionsGroovy2() {
        changeLanguageVersion("1.5")
    }

    @Test
    fun testChangeLanguageVersionInKotlinOptionsGroovy() {
        changeLanguageVersion("1.4")
    }


    @Test
    fun testEnableFeatureSupportGSKWithoutFoundKotlinVersion() {
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

    @Test
    @TargetVersions("4.7+")
    fun testEnableFeatureSupportToExistentArgumentsWithXFlag() = testEnableFeatureSupportToExistentArguments()

    @Test
    fun testChangeFeatureSupportGSK() {
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

    @Test
    @TargetVersions("4.7+")
    fun testChangeFeatureSupportGSKWithXFlag() = testChangeFeatureSupportGSK()

    @Test
    fun testDisableFeatureSupportGSK() {
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

    @Test
    @TargetVersions("4.7+")
    fun testDisableFeatureSupportGSKWithXFlag() = testDisableFeatureSupportGSK()

    @Test
    fun testEnableFeatureSupportGSK() {
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

    @Test
    @TargetVersions("4.7+")
    fun testEnableFeatureSupportGSKWithXFlag() = testEnableFeatureSupportGSK()

    @Test
    @TargetVersions("4.7+")
    fun testEnableFeatureSupportGSKWithXFlagModernKotlinSyntax() = testEnableFeatureSupportGSK()

    @Test
    @TargetVersions("4.7+")
    fun testEnableFeatureSupportGSKWithNotInfixVersionCallAndXFlag() = testEnableFeatureSupportGSK()

    @Test
    @TargetVersions("4.7+")
    fun testEnableFeatureSupportGSKWithSpecifyingPluginThroughIdAndXFlag() = testEnableFeatureSupportGSK()

    @Test
    @TargetVersions("7.6+")
    fun testAddToolchainAndFoojayKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )
                checkFiles(files, foojayPropertyMap)
            }
        }
    }

    @Test
    @TargetVersions("7.6+")
    fun testAddToolchainAndFoojayGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )
                checkFiles(files, foojayPropertyMap)
            }
        }
    }

    @Test
    @TargetVersions("7.6")
    fun testDontAddFoojayIfItsAlreadyAddedKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )
                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("7.6")
    fun testDontAddFoojayIfItsAlreadyAddedGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )
                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("7.6")
    fun testDontAddToolchainIfJvmTargetCompatibilityIs6() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val (kotlinVersionsAndModules, rootModuleKotlinVersion) = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                        myProject,
                        listOf(rootModule),
                        IdeKotlinVersion.get("1.8.0"),
                        collector,
                        kotlinVersionsAndModules,
                )
                checkFiles(files)
            }
        }
    }

    override fun testDataDirName(): String = "configurator"
}
