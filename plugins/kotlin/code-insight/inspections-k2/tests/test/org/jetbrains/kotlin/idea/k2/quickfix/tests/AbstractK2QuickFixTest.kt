// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractK2QuickFixTest : AbstractQuickFixTest() {
    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          { runInEdtAndWait { project.invalidateCaches() } },
          { super.tearDown() }
        )
    }

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override fun checkForUnexpectedErrors() {}

    override fun getAfterFileName(beforeFileName: String): String {
        val firAfterFile = File(dataFilePath(beforeFileName.replace(".kt", ".fir.kt.after")))
        return if (firAfterFile.exists()) {
            firAfterFile.name
        } else {
            super.getAfterFileName(beforeFileName)
        }
    }
}