// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractHighLevelQuickFixMultiFileTest : AbstractQuickFixMultiFileTest() {
    override fun isFirPlugin(): Boolean = true
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val captureExceptions: Boolean = false

    override fun checkForUnexpectedErrors(file: KtFile) {}

    override fun checkAvailableActionsAreExpected(file: File, actions: Collection<IntentionAction>) {}
}