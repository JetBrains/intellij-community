// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.application.UI
import com.intellij.openapi.wm.TextWidgetPresentation
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicInteger
import javax.accessibility.AccessibleContext
import javax.swing.JComponent

@TestApplication
internal class WidgetPresentationAccessibleDescriptionTest {
  private companion object {
    val project = projectFixture()
  }

  @Test
  fun `description is stored when the content changes`(): Unit = timeoutRunBlocking {
    val presentation = FakeTextPresentation()
    withWidgetPanel(presentation, expectedInitialDescription = "Go to line") { panel ->
      presentation.toolTip = "Line 2"
      val changed = withContext(Dispatchers.UI) { panel.descriptionChangedTo("Line 2") }
      presentation.text.value = "2:1"
      changed.await()
    }
  }

  @Test
  fun `reading the description does not enter a modal progress`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withWidgetPanel(FakeTextPresentation(), expectedInitialDescription = "Go to line") { panel ->
      val modalityChanges = AtomicInteger()
      ApplicationManager.getApplication().messageBus.connect(disposable)
        .subscribe(ModalityStateListener.TOPIC, object : ModalityStateListener {
          override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
            modalityChanges.incrementAndGet()
          }
        })
      val description = withContext(Dispatchers.UI) { panel.accessibleContext.accessibleDescription }

      assertThat(description).isEqualTo("Go to line")
      assertThat(modalityChanges.get()).isZero()
    }
  }

  @Test
  fun `a panel without a stored description derives it from the tooltip`(): Unit = timeoutRunBlocking {
    val description = withContext(Dispatchers.UI) {
      TextPanel { "<html>Plain <b>tooltip</b></html>" }.accessibleContext.accessibleDescription
    }

    assertThat(description).isEqualTo("Plain tooltip")
  }

  private suspend fun CoroutineScope.withWidgetPanel(
    presentation: TextWidgetPresentation,
    expectedInitialDescription: String,
    block: suspend (JComponent) -> Unit,
  ) {
    val widgetScope = childScope("widget")
    try {
      val (panel, initial) = withContext(Dispatchers.UI) {
        val panel = createComponentByWidgetPresentation(presentation, project.get(), widgetScope)
        panel to panel.descriptionChangedTo(expectedInitialDescription)
      }
      initial.await()
      block(panel)
    }
    finally {
      widgetScope.cancel()
    }
  }

  private fun JComponent.descriptionChangedTo(expected: String): CompletableDeferred<Unit> {
    val reached = CompletableDeferred<Unit>()
    accessibleContext.addPropertyChangeListener(object : PropertyChangeListener {
      override fun propertyChange(event: PropertyChangeEvent) {
        if (event.propertyName == AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY && event.newValue == expected) {
          accessibleContext.removePropertyChangeListener(this)
          reached.complete(Unit)
        }
      }
    })
    return reached
  }

  private class FakeTextPresentation : TextWidgetPresentation {
    val text = MutableStateFlow<String?>("1:1")

    @Volatile
    var toolTip: String = "<html>Go to <b>line</b></html>"

    override val alignment: Float
      get() = Component.CENTER_ALIGNMENT

    override fun text(): Flow<String?> = text

    override suspend fun getTooltipText(): String = toolTip
  }
}
