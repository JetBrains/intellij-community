// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@TestRoot("idea/tests")
@TestMetadata("testData/highlightingWithDependentLibraries")
@RunWith(JUnit38ClassRunner::class)
class HighlightingWithDependentLibrariesTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = object : KotlinLightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel) {
            val compiledJar1 = KotlinCompilerStandalone(listOf(File(testDataPath, "lib1"))).compile()
            val compiledJar2 = KotlinCompilerStandalone(listOf(File(testDataPath, "lib2")), classpath = listOf(compiledJar1)).compile()

            model.addLibraryEntry(createLibrary(module.project, compiledJar1, "baseLibrary"))
            model.addLibraryEntry(createLibrary(module.project, compiledJar2, "dependentLibrary"))
        }

        private fun createLibrary(project: Project, jarFile: File, name: String): Library {
            val library = LibraryTablesRegistrar.getInstance()!!.getLibraryTable(project).createLibrary(name)
            val model = library.modifiableModel
            model.addRoot(VfsUtil.getUrlForLibraryRoot(jarFile), OrderRootType.CLASSES)
            model.commit()
            return library
        }
    }

    fun testHighlightingWithDependentLibraries() {
        myFixture.configureByFile("module/usingLibs.kt")
        myFixture.checkHighlighting(false, false, false)
    }
}
