// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.AbstractConfigureProjectByChangingFileTest
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleModuleConfigurator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import java.io.File

abstract class AbstractGradleConfigureProjectByChangingFileTest :
    AbstractConfigureProjectByChangingFileTest<KotlinWithGradleConfigurator>() {

    fun doTestGradle(unused: String?) {
        val path = testDataPath
        val (before, after) = beforeAfterFiles()
        doTest(before, after, KotlinGradleModuleConfigurator())
    }

    private fun beforeAfterFiles(): Pair<String, String> {
        val testFile = File(testDataPath)
        val path = testFile.path

        if (testFile.isFile) {
            return path to path.replace("before", "after")
        }

        return when {
            File(testFile, "build_before.gradle").exists() ->
                "build_before.gradle" to "build_after.gradle"

            File(testFile, "build_before.gradle.kts").exists() ->
                "build_before.gradle.kts" to "build_after.gradle.kts"

            else -> error("Can't find test data files")
        }
    }

    override fun runConfigurator(
        module: Module,
        file: PsiFile,
        configurator: KotlinWithGradleConfigurator,
        version: IdeKotlinVersion,
        jvmTarget: String,
        collector: NotificationMessageCollector
    ) {
        if (file !is GroovyFile && file !is KtFile) {
            fail("file $file is not a GroovyFile or KtFile")
            return
        }

        configurator.configureModule(module, file, true, version, jvmTarget, collector, ChangedConfiguratorFiles())
        configurator.configureModule(module, file, false, version, jvmTarget, collector, ChangedConfiguratorFiles())
    }

    override fun getProjectJDK(): Sdk {
        val beforeAfterFiles = beforeAfterFiles()
        val (before, _) = beforeAfterFiles
        val gradleFile = File(testDataPath, before)

        if (gradleFile.readText().contains("1.9")) {
            return IdeaTestUtil.getMockJdk9()
        } else {
            return super.getProjectJDK()
        }
    }
}
