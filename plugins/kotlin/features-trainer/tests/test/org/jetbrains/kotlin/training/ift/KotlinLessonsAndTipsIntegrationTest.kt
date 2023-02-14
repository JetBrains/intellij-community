// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import training.simple.LessonsAndTipsIntegrationTest

@RunWith(JUnit4::class)
class KotlinLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
    override val languageId = "kotlin"
    override val languageSupport = KotlinLangSupport()
    override val learningCourse = KotlinLearningCourse()
}