// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.TechnologyFilter
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** The daemon runs the annotators that apply to the project of the file, see [TechnologyFilter]. */
@TestApplication
internal class AnnotatorTechnologiesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val annotatorModule = DefaultPluginDescriptor(PluginId.getId("technology.annotator"), javaClass.classLoader)

  @Test
  fun `the daemon skips an annotator of a module that does not apply to the project`(@TestDisposable disposable: Disposable) {
    timeoutRunBlocking(60.seconds) {
      val bean = LanguageExtensionPoint<Annotator>(PlainTextLanguage.INSTANCE.id, MarkingAnnotator())
      bean.pluginDescriptor = annotatorModule
      LanguageAnnotators.EP_NAME.point.registerExtension(bean, annotatorModule, disposable)
      val filter = FakeFilter(project, annotatorModule)
      ApplicationManager.getApplication().registerOrReplaceServiceInstance(TechnologyFilter::class.java, filter, disposable)

      codeInsightFixture.configureByText("test.txt", "some text")
      assertThat(problems()).contains(PROBLEM_DESCRIPTION)

      filter.changeApplicable(false)
      assertThat(problems()).doesNotContain(PROBLEM_DESCRIPTION)

      filter.changeApplicable(true)
      assertThat(problems()).contains(PROBLEM_DESCRIPTION)
    }
  }

  private suspend fun problems(): List<String?> {
    return withContext(Dispatchers.Default) { codeInsightFixture.doHighlighting(HighlightSeverity.ERROR) }.map { it.description }
  }
}

private const val PROBLEM_DESCRIPTION = "problem of the test annotator"

/** Decides if [module] applies to [project]. Every other module applies. */
private class FakeFilter(private val project: Project, private val module: PluginDescriptor) : TechnologyFilter {
  @Volatile
  private var applicable = true

  override fun isRestricted(project: Project): Boolean = project === this.project

  override fun isApplicable(module: PluginDescriptor, project: Project): Boolean {
    return project !== this.project || module !== this.module || applicable
  }

  /** Changes the answer for [module] and tells the listeners, as the filter of a product does. */
  fun changeApplicable(value: Boolean) {
    applicable = value
    ApplicationManager.getApplication().messageBus.syncPublisher(TechnologyFilter.TOPIC).applicabilityChanged(project)
  }
}

/** Marks the text of a plain text file as an error. The daemon creates the instances by reflection. */
private class MarkingAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is PsiPlainText) {
      holder.newAnnotation(HighlightSeverity.ERROR, PROBLEM_DESCRIPTION).range(element).create()
    }
  }
}
