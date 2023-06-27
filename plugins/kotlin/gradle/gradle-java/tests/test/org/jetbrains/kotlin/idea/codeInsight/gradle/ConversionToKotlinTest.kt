// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

import org.jetbrains.kotlin.idea.configuration.*

class ConversionToKotlinTest : KotlinGradleImportingTestCase() {

    @Test
    @TargetVersions("7.6+")
    fun testRootModuleContainsAnotherKotlinVersion() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                  myProject,
                  listOf(subModule),
                  IdeKotlinVersion.get("1.9.0"),
                  collector,
                  kotlinVersionsAndModules,
                )

                val subModules = listOf("app", "app1")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("7.6+")
    fun testRootAndSubmoduleContainTheSameKotlinVersion() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                  myProject,
                  listOf(subModule),
                  IdeKotlinVersion.get("1.8.0"),
                  collector,
                  kotlinVersionsAndModules,
                )

                val subModules = listOf("app", "app1")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }

    @Test
    @TargetVersions("7.6+")
    fun testConfigureProjectWithoutSubmodulesGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testConfigureProjectWithoutSubmodulesKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testAddVersionToRootIfNoSettingsFile() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testInheritVersionFromRootBuildScript() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testInheritVersionFromSettingsFile() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testAddKotlinToSubmoduleAndNotToRootBuildGradle() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testGroovyModuleInKotlinProject() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                  myProject,
                  listOf(subModule, rootModule),
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
    @TargetVersions("7.6+")
    fun testKotlinModuleInGroovyProject() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(
                  myProject,
                  listOf(subModule, rootModule),
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
    @TargetVersions("7.6+")
    fun testConfigureGroovyModuleInKotlinProject() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testConfigureKotlinModuleInGroovyProject() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testSettingsFileHasPluginManagementGroovy() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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
    @TargetVersions("7.6+")
    fun testSettingsFileHasPluginManagementKts() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val rootModule = ModuleManager.getInstance(myProject).findModuleByName("project")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
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

    /**
     * This test fails expectedly:
     * Error resolving plugin [id: 'org.jetbrains.kotlin.jvm', version: '1.7.0']
     * > The request for this plugin could not be satisfied because the plugin is already on the classpath with a different version (1.8.0).
     *
     * Maybe we'll want to fix it in the future.
     */
    /*    @Test
    @TargetVersions("7.6+")
    fun testRootModuleAndSubModuleContainsSameKotlinVersionAndSubmoduleDifferent() {
        val files = importProjectFromTestData()

        runInEdtAndWait {
            runWriteAction {
                val subModule = ModuleManager.getInstance(myProject).findModuleByName("project.app")!!
                val configurator = findGradleModuleConfigurator()
                val collector = NotificationMessageCollector.create(myProject)
                val kotlinVersionsAndModules = getKotlinVersionsAndModules(myProject, configurator)
                configurator.configureWithVersion(myProject, listOf(subModule), IdeKotlinVersion.get("1.8.0"), collector, kotlinVersionsAndModules)

                val subModules = listOf("app", "app1, app2")
                checkFilesInMultimoduleProject(files, subModules)
            }
        }
    }*/

    private fun findGradleModuleConfigurator(): KotlinGradleModuleConfigurator {
        return KotlinProjectConfigurator.EP_NAME.findExtensionOrFail(KotlinGradleModuleConfigurator::class.java)
    }

    override fun testDataDirName(): String = "kotlinVersionUplift"
}