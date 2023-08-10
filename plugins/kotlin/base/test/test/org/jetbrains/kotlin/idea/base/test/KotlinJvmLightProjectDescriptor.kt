// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import java.nio.file.Path

class KotlinJvmLightProjectDescriptor(
    private val testLibraries: List<KotlinTestLibrary>,
    private val javaLanguageLevel: LanguageLevel,
    private val attachJdkAnnotations: Boolean
) : LightProjectDescriptor() {
    override fun setUpProject(project: Project, handler: SetupHandler) {
        if (javaLanguageLevel.isPreview || javaLanguageLevel == LanguageLevel.JDK_X) {
            AcceptedLanguageLevelsSettings.allowLevel(project, javaLanguageLevel)
        }

        super.setUpProject(project, handler)
    }

    override fun getModuleTypeId(): String {
        return ModuleTypeId.JAVA_MODULE
    }

    override fun getSdk(): Sdk {
        val jdk = IdeaTestUtil.getMockJdk(javaLanguageLevel.toJavaVersion())
        return if (attachJdkAnnotations) PsiTestUtil.addJdkAnnotations(jdk) else jdk
    }

    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)

        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = javaLanguageLevel

        setupTestLibraries(model)
    }

    private fun setupTestLibraries(model: ModifiableRootModel) {
        val libraryTableModel = model.moduleLibraryTable.modifiableModel

        for (testLibrary in testLibraries) {
            val library = libraryTableModel.createLibrary(testLibrary.name)
            val libraryModel = library.modifiableModel

            fun addRoot(path: Path, rootType: OrderRootType) {
                val virtualFile = VfsUtil.getUrlForLibraryRoot(path)
                libraryModel.addRoot(virtualFile, rootType)
            }

            testLibrary.classes.forEach { addRoot(it, OrderRootType.CLASSES) }
            testLibrary.sources.forEach { addRoot(it, OrderRootType.SOURCES) }

            libraryModel.commit()
        }

        libraryTableModel.commit()
    }

    companion object {
        val DEFAULT = KotlinJvmLightProjectDescriptor(
            testLibraries = listOf(KotlinTestLibrary.kotlinStdlibJvm(LanguageLevel.JDK_1_8)),
            javaLanguageLevel = LanguageLevel.JDK_1_8,
            attachJdkAnnotations = false
        )
    }
}