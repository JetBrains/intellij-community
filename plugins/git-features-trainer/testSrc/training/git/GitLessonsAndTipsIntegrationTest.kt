// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.git

import com.intellij.testFramework.junit5.TestApplication
import training.lang.LangSupport
import training.simple.LessonsAndTipsIntegrationTest

@TestApplication
class GitLessonsAndTipsIntegrationTest : LessonsAndTipsIntegrationTest() {
  override val languageId: String? = null
  override val languageSupport: LangSupport? = null
  override val learningCourse = GitLearningCourse()
}