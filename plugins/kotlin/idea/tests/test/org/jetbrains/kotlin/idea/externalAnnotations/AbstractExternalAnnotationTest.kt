// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.externalAnnotations

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.JavaModuleExternalPaths
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractExternalAnnotationTest: KotlinLightCodeInsightFixtureTestCase()  {

    override fun setUp() {
        super.setUp()
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true
        addFile(dataFilePath(classWithExternalAnnotatedMembers))
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = false },
            ThrowableRunnable { super.tearDown() }
        )
    }

    private fun addFile(path: String) {
        val file = File(path)
        val root = LightPlatformTestCase.getSourceRoot()
        runWriteAction {
            val virtualFile = root.createChildData(null, file.name)
            virtualFile.getOutputStream(null).writer().use { it.write(FileUtil.loadFile(file)) }
        }
    }

    protected fun doTest(kotlinFilePath: String) {
        myFixture.configureByFiles(kotlinFilePath, dataFilePath(externalAnnotationsFile), dataFilePath(classWithExternalAnnotatedMembers))
        myFixture.checkHighlighting()
    }

    override fun getProjectDescriptor() = object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel) {
            super.configureModule(module, model)
            model.getModuleExtension(JavaModuleExternalPaths::class.java)
                .setExternalAnnotationUrls(arrayOf(VfsUtilCore.pathToUrl(dataFilePath(externalAnnotationsPath))))
        }
    }

    companion object {
        private const val externalAnnotationsPath = "annotations/"
        private const val classWithExternalAnnotatedMembers = "ClassWithExternalAnnotatedMembers.java"
        private const val externalAnnotationsFile = "$externalAnnotationsPath/annotations.xml"
    }
}