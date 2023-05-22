// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.copyright

import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractUpdateKotlinCopyrightTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(path: String) {
        val testName = File(path).name
        myFixture.configureByFile(testName)
        configureCopyright()
        myFixture.checkResultByFile("$testName.after")
    }

    private fun configureCopyright() {
        val profile = CopyrightProfile().apply {
            notice = "Copyright notice\nOver multiple lines"
        }
        UpdateCopyrightFactory.createUpdateCopyright(project, module, myFixture.file, profile)!!.apply {
            prepare()
            complete()
        }
    }
}