// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.session.KtAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import java.io.File

@OptIn(KtAnalysisApiInternals::class)
fun Project.invalidateCaches() {
    JavaLibraryModificationTracker.incModificationCount(this)
    service<KotlinModificationTrackerFactory>().incrementModificationsCount()
    service<KtAnalysisSessionProvider>().clearCaches()
}

fun addExternalTestFiles(testDataFilePath: String) {
    File(testDataFilePath).getExternalFiles().forEach(::addFile)
}

private fun addFile(file: File) {
    addFile(FileUtil.loadFile(file, /* convertLineSeparators = */true), file.name)
}

private fun File.getExternalFiles(): List<File> {
    val directory = parentFile
    val externalFileName = "${nameWithoutExtension}.external"
    return directory.listFiles { _, name ->
        name == "$externalFileName.kt" || name == "$externalFileName.java"
    }!!.filterNotNull()
}


private fun addFile(text: String, fileName: String) {
    runWriteAction {
        val virtualDir = LightPlatformTestCase.getSourceRoot()!!
        val virtualFile = virtualDir.createChildData(null, fileName)
        virtualFile.getOutputStream(null)!!.writer().use { it.write(text) }
    }
}