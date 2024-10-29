// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.ui.KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME
import org.junit.Assert

abstract class AbstractConfigureKotlinTest : AbstractConfigureKotlinTestBase() {
    protected fun doTestConfigureModulesWithNonDefaultSetup(configurator: KotlinWithLibraryConfigurator<*>) {
        modules.forEach { assertNotConfigured(it, configurator) }
        configurator.configure(myProject, emptyList())
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        modules.forEach { assertProperlyConfigured(it, configurator) }
    }

    override fun setUp() {
        super.setUp()
        waitForKotlinSettingsConfiguration()
    }

    private fun waitForKotlinSettingsConfiguration() {
        // Make sure that the Kotlin compiler settings are initialized
        KotlinCommonCompilerArgumentsHolder.getInstance(project)
        // Updating the settings is done in a coroutine that might take several EDT dispatches to work.
        // We dispatch them up to 10 times here to ensure the settings are updated correctly.
        val propertiesComponent = PropertiesComponent.getInstance(project)
        for (i in 1..5) {
            if (propertiesComponent.isValueSet(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME)) break
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(100)
        }
        Assert.assertTrue(propertiesComponent.isValueSet(KOTLIN_LANGUAGE_VERSION_CONFIGURED_PROPERTY_NAME))
    }

    protected fun doTestSingleJvmModule() {
        doTestSingleModule(jvmConfigurator)
    }

    protected fun doTestSingleJsModule() {
        doTestSingleModule(jsConfigurator)
    }

    private fun doTestSingleModule(configurator: KotlinWithLibraryConfigurator<*>) {
        assertNotConfigured(module, configurator)
        configure(module, configurator)
        assertProperlyConfigured(module, configurator)
    }

    protected fun assertProperlyConfigured(module: Module, configurator: KotlinWithLibraryConfigurator<*>) {
        assertConfigured(module, configurator)
        assertNotConfigured(module, getOppositeConfigurator(configurator))
    }

    protected fun assertNotConfigured(module: Module, configurator: KotlinWithLibraryConfigurator<*>) {
        TestCase.assertFalse(
            String.format("Module %s should not be configured as %s Module", module.name, configurator.presentableText),
            configurator.isConfigured(module)
        )
    }

    protected fun assertConfigured(module: Module, configurator: KotlinWithLibraryConfigurator<*>) {
        TestCase.assertTrue(
            String.format("Module %s should be configured with configurator '%s'", module.name, configurator.presentableText),
            configurator.isConfigured(module)
        )
    }

    private fun configure(modules: List<Module>, configurator: KotlinWithLibraryConfigurator<*>) {
        val project = modules.first().project
        val collector = NotificationMessageCollector.create(project)

        configurator.getOrCreateKotlinLibrary(project, collector)
        val writeActions = ArrayList<() -> Unit>()
        for (module in modules) {
            ReadAction.nonBlocking {
                configurator.configureModule (module, collector, writeActions)
            }.submit(AppExecutorUtil.getAppExecutorService()).get()
        }
        ApplicationManager.getApplication().runWriteAction{
            writeActions.forEach { writeAction ->
              writeAction.invoke()
            }
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        collector.showNotification()
    }

    protected fun configure(module: Module, configurator: KotlinProjectConfigurator) {
        if (configurator is KotlinJavaModuleConfigurator) {
            configure(listOf(module), configurator as KotlinWithLibraryConfigurator<*>)
        }

        if (configurator is KotlinJsModuleConfigurator) {
            configure(listOf(module), configurator as KotlinWithLibraryConfigurator<*>)
        }
    }

    override fun getTestProjectJdk(): Sdk = IdeaTestUtil.createMockJdk("1.8", IdeaTestUtil.getMockJdk18Path().path)
}
