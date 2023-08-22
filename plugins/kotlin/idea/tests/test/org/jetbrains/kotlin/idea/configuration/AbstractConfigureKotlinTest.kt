// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import junit.framework.TestCase

abstract class AbstractConfigureKotlinTest : AbstractConfigureKotlinTestBase() {
    protected fun doTestConfigureModulesWithNonDefaultSetup(configurator: KotlinWithLibraryConfigurator<*>) {
        modules.forEach { assertNotConfigured(it, configurator) }
        configurator.configure(myProject, emptyList())
        modules.forEach { assertProperlyConfigured(it, configurator) }
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
