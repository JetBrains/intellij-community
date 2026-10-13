// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Allows inserting additional status information into the main changes view
 */
@ApiStatus.Internal
interface ChangeListManagerStatusProvider {
  /**
   * @return the component representing the status or `null`
   */
  @RequiresEdt
  fun getStatusComponent(project: Project): JComponent?

  companion object {
    /**
     * Subscribe to the status updates in [project]
     */
    suspend fun consumeStatusComponents(project: Project, @RequiresEdt componentsConsumer: (List<JComponent>) -> Unit): Nothing {
      fun update() {
        val components = EP_NAME.extensionList.mapNotNull {
          it.getStatusComponent(project)
        }
        componentsConsumer(components)
      }

      withContext(Dispatchers.UI + ModalityState.nonModal().asContextElement()) {
        project.getMessageBus().connect(this).subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
          override fun changedFileStatusChanged() {
            launch {
              update()
            }
          }
        })

        EP_NAME.addChangeListener(this, {
          launch {
            update()
          }
        })
        update()
        awaitCancellation()
      }
    }

    private val EP_NAME = ExtensionPointName.create<ChangeListManagerStatusProvider>("com.intellij.changeListManagerStatusProvider")
  }
}