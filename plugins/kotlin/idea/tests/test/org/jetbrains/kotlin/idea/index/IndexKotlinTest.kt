// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.index

import com.intellij.idea.Bombed
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.nio.file.Path
import kotlin.reflect.full.staticProperties

@TestRoot("idea/tests")
@TestMetadata("testData/index")
@RunWith(JUnit38ClassRunner::class)
class IndexKotlinTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = object : KotlinLightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel) {
            createLibrary(module.project, "lib")?.let(model::addLibraryEntry)
        }

        private fun createLibrary(project: Project, name: String): Library? {
            val path = dataFilePath().resolve("lib").takeIf(Path::exists) ?: return null
            val library = LibraryTablesRegistrar.getInstance()!!.getLibraryTable(project).createLibrary(name)
            val model = library.modifiableModel
            model.addRoot(VfsUtil.getUrlForLibraryRoot(path), OrderRootType.CLASSES)
            model.commit()
            return library
        }
    }

    @TestMetadata("brokenClass")
    fun testBrokenClass() {
        myFixture.createFile("1.kt", "fun foo() = Unit")
    }

    @Bombed(year = 2023, month = 1, day = 31, user = "Yuriy Artamonov")
    fun testKotlinFileIsKnownDuplicatedIdViolation() {
        assertFalse("KtStubElementTypes must not declare FILE field",
                    KtStubElementTypes::class.staticProperties.any { it.name == "FILE" })

        assertTrue("""
            |StubSerializerEnumerator.isKnownDuplicatedIdViolation ignores kotlin.FILE.
            |Remove kotlin.FILE from isKnownDuplicatedIdViolation once fixed in Kotlin compiler, expected in 1.8.20
        """.trimMargin(), false)
    }
}