// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.navigation


import com.intellij.testFramework.TestIndexingModeSupporter
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.navigation.AbstractKotlinGotoTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

abstract class AbstractFirGotoTest: AbstractKotlinGotoTest() {

    override fun getIgnoreDirective(): String = "IGNORE_K2"

    override fun getExpectedFile(nioPath: Path?): Path {
        val expectedFile = super.getExpectedFile(nioPath)
        if (indexingMode == TestIndexingModeSupporter.IndexingMode.DUMB_FULL_INDEX) {
            expectedFile.parent.resolve("${expectedFile.fileName.nameWithoutExtension}.dumb.txt").takeIf { it.exists() }?.let {
                return it
            }
        }
        return expectedFile
    }

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}