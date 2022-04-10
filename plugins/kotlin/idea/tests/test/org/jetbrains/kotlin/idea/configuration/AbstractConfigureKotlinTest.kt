// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
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
        val collector = createConfigureKotlinNotificationCollector(project)

        configurator.getOrCreateKotlinLibrary(project, collector)
        for (module in modules) {
            configurator.configureModule(module, collector)
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
