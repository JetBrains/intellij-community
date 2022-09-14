// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.simple

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerExtension
import org.junit.Assert
import org.junit.Test
import training.lang.LangSupport
import training.lang.LangSupportBean
import training.learn.course.LearningCourse

abstract class LessonsAndTipsIntegrationTest : BasePlatformTestCase() {
  protected abstract val languageId: String?
  protected abstract val languageSupport: LangSupport?
  protected abstract val learningCourse: LearningCourse

  override fun setUp() {
    super.setUp()

    val langId = languageId
    val langSupport = languageSupport
    val EP_NAME = ExtensionPointName<LangSupportBean>(LangSupport.EP_NAME)
    if (langId != null && langSupport != null && EP_NAME.extensionList.find { it.language == langId } == null) {
      val langExtension = LangSupportBean(langId, langSupport)
      // specify fake descriptor because it is required to be not null, but will not be used, because extension instance already created
      langExtension.pluginDescriptor = DefaultPluginDescriptor("")
      ApplicationManager.getApplication().registerExtension(EP_NAME, langExtension, testRootDisposable)
    }
  }

  @Test
  fun `All lessons specified in tips map exist`() {
    val lessonIdToTipsMap = learningCourse.getLessonIdToTipsMap()
    val lessonIds = learningCourse.modules().flatMap { it.lessons }.map { it.id }.toSet()

    val unknownLessonIds = lessonIdToTipsMap.keys.filter { !lessonIds.contains(it) }
    if (unknownLessonIds.isNotEmpty()) {
      Assert.fail("Course $learningCourse do not contain lessons with ids:\n${unknownLessonIds.joinToString("\n")}")
    }
  }
}