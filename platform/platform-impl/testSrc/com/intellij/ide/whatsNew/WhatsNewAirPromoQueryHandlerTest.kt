// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class WhatsNewAirPromoQueryHandlerTest {
  @Test
  fun `opening a page reports a show`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val listener = RecordingPromoListener()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(WhatsNewAirPromoListener.TOPIC, listener)
    WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project), true, null)

    assertThat(listener.shownProjects).containsExactly(project)
  }

  @Test
  fun `a page without the block reports no show`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val listener = RecordingPromoListener()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(WhatsNewAirPromoListener.TOPIC, listener)
    WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project), false, null)

    assertThat(listener.shownProjects).isEmpty()
  }

  @Test
  fun `opening another page reports another show`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val listener = RecordingPromoListener()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(WhatsNewAirPromoListener.TOPIC, listener)
    WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project), true, null)
    WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project), true, null)

    assertThat(listener.shownProjects).containsExactly(project, project)
  }

  @Test
  fun `the Air action request keeps its existing delegate`(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val delegate = RecordingPromoQueryHandler()
    val handler = WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project, true), true, delegate)

    assertThat(handler!!.query(1, AIR_PROMO_ACTION_ID)).isEqualTo("true")
    assertThat(delegate.requests).containsExactly(AIR_PROMO_ACTION_ID)
  }

  @Test
  fun `the promo request keeps the existing delegate decision`(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val delegate = RecordingPromoQueryHandler("false")
    val handler = WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project, true), true, delegate)

    assertThat(handler!!.query(1, AIR_PROMO_REQUEST)).isEqualTo("false")
    assertThat(delegate.requests).containsExactly(AIR_PROMO_REQUEST)
  }

  @Test
  fun `a page without a delegate keeps no query handler`(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val handler = WhatsNewAirPromoQueryHandler.create(WhatsNewAirPromoPage(project, true), true, null)

    assertThat(handler).isNull()
  }
}

private class RecordingPromoListener : WhatsNewAirPromoListener {
  val shownProjects = ArrayList<Project>()

  override fun blockShown(page: WhatsNewAirPromoPage) {
    shownProjects += page.project
  }

  override fun blockClicked(page: WhatsNewAirPromoPage) {
    error("No main button click is expected")
  }

  override fun tooltipShown(project: Project) = Unit
}

private class RecordingPromoQueryHandler(private val response: String = "true") : HTMLEditorProvider.JsQueryHandler {
  val requests = ArrayList<String>()

  override suspend fun query(id: Long, request: String): String {
    requests += request
    return response
  }
}
